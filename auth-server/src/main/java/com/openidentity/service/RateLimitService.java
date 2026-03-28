package com.openidentity.service;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Token-endpoint rate limiter with optional Redis-backed HA mode.
 *
 * <p><strong>Redis mode</strong> (when Redis is configured and
 * {@code openidentity.rate-limit.redis-enabled=true}): uses {@code INCR} plus
 * {@code EXPIRE} with a 60-second sliding window per client key. All replicas
 * share the same counter, so the limit is enforced correctly across a
 * horizontally scaled deployment.
 *
 * <p><strong>Fallback mode</strong> (when Redis is disabled, unavailable, or
 * not configured): falls back transparently to a JVM-local
 * {@link ConcurrentHashMap}. A warning is logged on startup so the operator is
 * aware. The fallback multiplies the effective limit by replica count, which is
 * acceptable for single-node dev or staging but not for production HA.
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@code openidentity.rate-limit.token.rpm} - allowed requests per minute per client key
 *   <li>{@code openidentity.rate-limit.token.enabled} - disable entirely
 *   <li>{@code openidentity.rate-limit.redis-enabled} - enable shared Redis-backed rate limiting
 *   <li>{@code quarkus.redis.hosts} - Redis URL used when shared mode is enabled
 * </ul>
 */
@ApplicationScoped
public class RateLimitService {

  private static final Logger LOG = Logger.getLogger(RateLimitService.class);
  private static final long WINDOW_SECONDS = 60L;
  private static final long WINDOW_MS = WINDOW_SECONDS * 1_000L;

  @ConfigProperty(name = "openidentity.rate-limit.token.rpm", defaultValue = "60")
  int limitRpm;

  @ConfigProperty(name = "openidentity.rate-limit.token.enabled", defaultValue = "true")
  boolean enabled;

  @ConfigProperty(name = "openidentity.rate-limit.redis-enabled", defaultValue = "false")
  boolean redisEnabled;

  @Inject
  Instance<RedisDataSource> redisDataSource;

  private KeyCommands<String> keyCommands;
  private ValueCommands<String, Long> valueCommands;
  private boolean redisAvailable = false;

  private static final class Bucket {
    int count;
    long windowStart;
  }

  private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();

  @PostConstruct
  void init() {
    if (redisEnabled && redisDataSource.isResolvable()) {
      try {
        RedisDataSource dataSource = redisDataSource.get();
        keyCommands = dataSource.key();
        valueCommands = dataSource.value(Long.class);
        redisAvailable = true;
        LOG.infof("Rate limiter: Redis mode enabled (limit=%d rpm)", limitRpm);
      } catch (Exception e) {
        LOG.warnf(
            "Rate limiter: Redis connection failed, falling back to in-memory. Error: %s",
            e.getMessage());
        redisAvailable = false;
      }
    } else {
      LOG.warn(
          "Rate limiter: shared Redis mode disabled or unavailable; using in-memory fallback. "
              + "This is not suitable for multi-replica deployments. "
              + "Set openidentity.rate-limit.redis-enabled=true and configure "
              + "quarkus.redis.hosts to enable shared rate limiting.");
    }
  }

  /**
   * Returns true if the request for {@code clientKey} should be blocked.
   */
  public boolean isRateLimited(String clientKey) {
    if (!enabled) {
      return false;
    }
    String key = clientKey != null && !clientKey.isBlank() ? clientKey : "anon";
    return redisAvailable ? redisRateLimit(key) : localRateLimit(key);
  }

  private boolean redisRateLimit(String key) {
    try {
      String redisKey = "oi:rl:token:" + key;
      Long count = valueCommands.incr(redisKey);
      if (count == 1L) {
        keyCommands.expire(redisKey, Duration.ofSeconds(WINDOW_SECONDS));
      }
      return count > limitRpm;
    } catch (Exception e) {
      LOG.warnf("Rate limiter: Redis error for key %s, allowing request: %s", key, e.getMessage());
      return false;
    }
  }

  private boolean localRateLimit(String key) {
    long now = Instant.now().toEpochMilli();
    Bucket bucket = localBuckets.computeIfAbsent(key, ignored -> {
      Bucket created = new Bucket();
      created.windowStart = now;
      created.count = 0;
      return created;
    });
    synchronized (bucket) {
      if (now - bucket.windowStart > WINDOW_MS) {
        bucket.windowStart = now;
        bucket.count = 0;
      }
      bucket.count++;
      return bucket.count > limitRpm;
    }
  }
}
