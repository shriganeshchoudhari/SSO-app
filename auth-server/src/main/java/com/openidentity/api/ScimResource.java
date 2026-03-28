package com.openidentity.api;

import com.openidentity.domain.RealmEntity;
import com.openidentity.domain.ScimGroupEntity;
import com.openidentity.domain.ScimGroupMemberEntity;
import com.openidentity.domain.ScimUserEntity;
import com.openidentity.service.ScimProvisioningService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * SCIM 2.0 resource server — Phase 4 skeleton.
 *
 * <p>Implements the minimum endpoints required for enterprise directory
 * inbound provisioning (IdP-pushed users). Designed against RFC 7644.
 *
 * <p>Current scope (in-progress):
 * <ul>
 *   <li>GET  /scim/v2/realms/{realm}/ServiceProviderConfig
 *   <li>GET  /scim/v2/realms/{realm}/Schemas
 *   <li>GET  /scim/v2/realms/{realm}/Users         — list with filter/pagination
 *   <li>POST /scim/v2/realms/{realm}/Users         — create / provision
 *   <li>GET  /scim/v2/realms/{realm}/Users/{id}    — get by SCIM id
 *   <li>PUT  /scim/v2/realms/{realm}/Users/{id}    — full replace
 *   <li>PATCH /scim/v2/realms/{realm}/Users/{id}   — partial update (active flag)
 *   <li>DELETE /scim/v2/realms/{realm}/Users/{id}  — deprovision
 * </ul>
 *
 * <p>Remaining gaps (planned):
 * <ul>
 *   <li>Push provisioning back to external directories
 * </ul>
 */
@Path("/scim/v2/realms/{realm}")
@Produces("application/scim+json")
@Consumes("application/scim+json")
@Tag(name = "SCIM", description = "SCIM 2.0 provisioning endpoints")
public class ScimResource {

  private static final String SCIM_USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
  private static final String SCIM_GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";
  private static final String SCIM_LIST_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
  private static final String SCIM_ERROR = "urn:ietf:params:scim:api:messages:2.0:Error";
  private static final String SCIM_PATCH_OP = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
  private static final String SCIM_BULK_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:BulkResponse";
  private static final int DEFAULT_COUNT = 100;
  private static final int DEFAULT_BULK_MAX_OPS = 100;
  private static final int DEFAULT_BULK_MAX_PAYLOAD = 1024 * 1024;

  @Inject EntityManager em;
  @Inject ScimProvisioningService scimProvisioningService;

  // ── Service provider config ────────────────────────────────────────────────

  @GET
  @Path("/ServiceProviderConfig")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "SCIM service provider configuration")
  public Map<String, Object> serviceProviderConfig(@PathParam("realm") String realmName) {
    requireRealm(realmName);
    Map<String, Object> cfg = new LinkedHashMap<>();
    cfg.put("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"));
    cfg.put("documentationUri", "https://github.com/your-org/openidentity");
    cfg.put("patch",    Map.of("supported", true));
    cfg.put("bulk",     Map.of(
        "supported", true,
        "maxOperations", DEFAULT_BULK_MAX_OPS,
        "maxPayloadSize", DEFAULT_BULK_MAX_PAYLOAD));
    cfg.put("supportedSchemas", List.of(
        "urn:ietf:params:scim:schemas:core:2.0:User",
        "urn:ietf:params:scim:schemas:core:2.0:Group"
    ));
    cfg.put("filter",   Map.of("supported", true, "maxResults", 200));
    cfg.put("changePassword", Map.of("supported", false));
    cfg.put("sort",     Map.of("supported", false));
    cfg.put("etag",     Map.of("supported", false));
    cfg.put("authenticationSchemes", List.of(Map.of(
        "type", "oauthbearertoken",
        "name", "OAuth Bearer Token",
        "description", "Authentication using an OAuth2 Bearer Token"
    )));
    return cfg;
  }

  // ── Schemas ────────────────────────────────────────────────────────────────

  @GET
  @Path("/Schemas")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "List supported SCIM schemas")
  public Map<String, Object> schemas(@PathParam("realm") String realmName) {
    requireRealm(realmName);
    Map<String, Object> userSchema = new LinkedHashMap<>();
    userSchema.put("id", SCIM_USER_SCHEMA);
    userSchema.put("name", "User");
    userSchema.put("description", "User account");
    userSchema.put("attributes", List.of(
        attr("userName",    "string",  true,  "uniqueness", "server"),
        attr("displayName", "string",  false, "uniqueness", "none"),
        attr("givenName",   "string",  false, "uniqueness", "none"),
        attr("familyName",  "string",  false, "uniqueness", "none"),
        attr("emails",      "complex", false, "uniqueness", "none"),
        attr("active",      "boolean", false, "uniqueness", "none"),
        attr("externalId",  "string",  false, "uniqueness", "none")
    ));
    Map<String, Object> groupSchema = new LinkedHashMap<>();
    groupSchema.put("id", SCIM_GROUP_SCHEMA);
    groupSchema.put("name", "Group");
    groupSchema.put("description", "Group resource");
    groupSchema.put("attributes", List.of(
        attr("displayName", "string", true, "uniqueness", "server"),
        attr("externalId", "string", false, "uniqueness", "none"),
        attr("members", "complex", false, "uniqueness", "none")
    ));
    return Map.of("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"),
        "totalResults", 2,
        "Resources", List.of(userSchema, groupSchema));
  }

  @POST
  @Path("/Bulk")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({"application/scim+json", MediaType.APPLICATION_JSON})
  @Transactional
  @Operation(summary = "Execute SCIM bulk provisioning operations")
  public Response bulk(
      @PathParam("realm") String realmName,
      Map<String, Object> body) {
    requireRealm(realmName);
    if (String.valueOf(body).getBytes(StandardCharsets.UTF_8).length > DEFAULT_BULK_MAX_PAYLOAD) {
      return scimError(413, "Bulk payload exceeds maxPayloadSize");
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> operations = (List<Map<String, Object>>) body.get("Operations");
    if (operations == null || operations.isEmpty()) {
      return scimError(400, "Operations is required");
    }
    if (operations.size() > DEFAULT_BULK_MAX_OPS) {
      return scimError(413, "Bulk operation count exceeds maxOperations");
    }

    Integer failOnErrors = intField(body.get("failOnErrors"));
    int maxErrors = failOnErrors != null && failOnErrors > 0 ? failOnErrors : Integer.MAX_VALUE;
    int errorCount = 0;

    Map<String, UUID> bulkIdReferences = new LinkedHashMap<>();
    List<Map<String, Object>> bulkResponses = new ArrayList<>();

    for (Map<String, Object> operation : operations) {
      Map<String, Object> result = executeBulkOperation(realmName, operation, bulkIdReferences);
      bulkResponses.add(result);
      int status = Integer.parseInt(String.valueOf(result.get("status")));
      if (status >= 400) {
        errorCount++;
        if (errorCount >= maxErrors) {
          break;
        }
      }
    }

    Map<String, Object> responseBody = new LinkedHashMap<>();
    responseBody.put("schemas", List.of(SCIM_BULK_RESPONSE));
    responseBody.put("Operations", bulkResponses);
    return Response.ok(responseBody).build();
  }

  // ── Users — list ──────────────────────────────────────────────────────────

  @GET
  @Path("/Users")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "List SCIM users (inbound provisioned)")
  public Map<String, Object> listUsers(
      @PathParam("realm") String realmName,
      @QueryParam("startIndex") @DefaultValue("1") int startIndex,
      @QueryParam("count")      @DefaultValue("100") int count,
      @QueryParam("filter")     String filter) {
    requireRealm(realmName);
    RealmEntity realm = getRealmEntity(realmName);
    List<ScimUserEntity> filtered = em.createQuery(
            "select s from ScimUserEntity s where s.realm.id = :rid order by s.userName",
            ScimUserEntity.class)
        .setParameter("rid", realm.getId())
        .getResultList()
        .stream()
        .filter(user -> matchesUserFilter(user, filter))
        .toList();
    List<ScimUserEntity> results = paginate(filtered, startIndex, count);

    return Map.of(
        "schemas",      List.of(SCIM_LIST_RESPONSE),
        "totalResults", filtered.size(),
        "startIndex",   startIndex,
        "itemsPerPage", results.size(),
        "Resources",    results.stream().map(this::toScimUser).toList()
    );
  }

  // ── Users — create ─────────────────────────────────────────────────────────

  @POST
  @Path("/Users")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({"application/scim+json", MediaType.APPLICATION_JSON})
  @Transactional
  @Operation(summary = "Provision a SCIM user")
  public Response createUser(
      @PathParam("realm") String realmName,
      Map<String, Object> body) {
    RealmEntity realm = requireRealm(realmName);
    String userName = stringField(body, "userName");
    if (userName == null || userName.isBlank()) {
      return scimError(400, "userName is required");
    }

    // Idempotency: if a user with this userName already exists, return it
    var existing = findByUserName(realm, userName);
    if (existing != null) {
      return Response.status(409).entity(scimErrorBody(409, "User already exists")).build();
    }

    ScimUserEntity entity = new ScimUserEntity();
    entity.setId(UUID.randomUUID());
    entity.setRealm(realm);
    entity.setExternalId(stringField(body, "externalId"));
    entity.setUserName(userName);
    entity.setDisplayName(stringField(body, "displayName"));
    entity.setGivenName(nameField(body, "givenName"));
    entity.setFamilyName(nameField(body, "familyName"));
    entity.setEmail(emailField(body));
    entity.setActive(boolField(body, "active", true));
    entity.setProvisionedAt(OffsetDateTime.now());
    em.persist(entity);
    scimProvisioningService.syncLinkedUser(entity);

    return Response.status(201).entity(toScimUser(entity)).build();
  }

  // ── Users — get ───────────────────────────────────────────────────────────

  @GET
  @Path("/Users/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get SCIM user by ID")
  public Response getUser(
      @PathParam("realm") String realmName,
      @PathParam("id") UUID id) {
    requireRealm(realmName);
    var entity = em.find(ScimUserEntity.class, id);
    if (entity == null) return scimError(404, "User not found");
    return Response.ok(toScimUser(entity)).build();
  }

  // ── Users — full replace ──────────────────────────────────────────────────

  @PUT
  @Path("/Users/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({"application/scim+json", MediaType.APPLICATION_JSON})
  @Transactional
  @Operation(summary = "Replace SCIM user (full update)")
  public Response replaceUser(
      @PathParam("realm") String realmName,
      @PathParam("id") UUID id,
      Map<String, Object> body) {
    requireRealm(realmName);
    var entity = em.find(ScimUserEntity.class, id);
    if (entity == null) return scimError(404, "User not found");
    String userName = stringField(body, "userName");
    if (userName == null || userName.isBlank()) {
      return scimError(400, "userName is required");
    }
    if (userNameExists(entity.getRealm(), userName, entity.getId())) {
      return Response.status(409).entity(scimErrorBody(409, "User already exists")).build();
    }

    entity.setExternalId(stringField(body, "externalId"));
    entity.setUserName(userName);
    entity.setDisplayName(stringField(body, "displayName"));
    entity.setGivenName(nameField(body, "givenName"));
    entity.setFamilyName(nameField(body, "familyName"));
    entity.setEmail(emailField(body));
    entity.setActive(boolField(body, "active", true));
    entity.setLastSyncedAt(OffsetDateTime.now());
    em.merge(entity);
    scimProvisioningService.syncLinkedUser(entity);
    return Response.ok(toScimUser(entity)).build();
  }

  // ── Users — partial update (active flag) ──────────────────────────────────

  @PATCH
  @Path("/Users/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({"application/scim+json", MediaType.APPLICATION_JSON})
  @Transactional
  @Operation(summary = "Patch SCIM user (partial update — active flag supported)")
  public Response patchUser(
      @PathParam("realm") String realmName,
      @PathParam("id") UUID id,
      Map<String, Object> body) {
    requireRealm(realmName);
    var entity = em.find(ScimUserEntity.class, id);
    if (entity == null) return scimError(404, "User not found");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> operations = (List<Map<String, Object>>) body.get("Operations");
    if (operations == null || operations.isEmpty()) {
      return scimError(400, "Operations is required");
    }
    try {
      for (Map<String, Object> op : operations) {
        applyUserPatchOperation(entity, op);
      }
    } catch (WebApplicationException e) {
      return scimWebApplicationError(e, "Invalid SCIM user patch request");
    }
    entity.setLastSyncedAt(OffsetDateTime.now());
    em.merge(entity);
    scimProvisioningService.syncLinkedUser(entity);
    return Response.ok(toScimUser(entity)).build();
  }

  // ── Users — delete ────────────────────────────────────────────────────────

  @DELETE
  @Path("/Users/{id}")
  @Transactional
  @Operation(summary = "Deprovision SCIM user")
  public Response deleteUser(
      @PathParam("realm") String realmName,
      @PathParam("id") UUID id) {
    RealmEntity realm = requireRealm(realmName);
    var entity = em.find(ScimUserEntity.class, id);
    if (entity == null || !entity.getRealm().getId().equals(realm.getId())) {
      return scimError(404, "User not found");
    }
    scimProvisioningService.deprovisionLinkedUser(entity);
    em.remove(entity);
    return Response.noContent().build();
  }

  // ── Groups — list ──────────────────────────────────────────────────────────

  @GET
  @Path("/Groups")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "List SCIM groups")
  public Map<String, Object> listGroups(
      @PathParam("realm") String realmName,
      @QueryParam("startIndex") @DefaultValue("1") int startIndex,
      @QueryParam("count")      @DefaultValue("100") int count,
      @QueryParam("filter")     String filter) {
    RealmEntity realm = requireRealm(realmName);
    List<ScimGroupEntity> filtered = em.createQuery(
            "select g from ScimGroupEntity g where g.realm.id = :rid order by g.displayName",
            ScimGroupEntity.class)
        .setParameter("rid", realm.getId())
        .getResultList()
        .stream()
        .filter(group -> matchesGroupFilter(group, filter))
        .toList();
    List<ScimGroupEntity> results = paginate(filtered, startIndex, count);
    return Map.of(
        "schemas",      List.of(SCIM_LIST_RESPONSE),
        "totalResults", filtered.size(),
        "startIndex",   startIndex,
        "itemsPerPage", results.size(),
        "Resources",    results.stream().map(g -> toScimGroup(g, realmName)).toList()
    );
  }

  // ── Groups — create ───────────────────────────────────────────────────────

  @POST
  @Path("/Groups")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({"application/scim+json", MediaType.APPLICATION_JSON})
  @Transactional
  @Operation(summary = "Create SCIM group")
  public Response createGroup(
      @PathParam("realm") String realmName,
      Map<String, Object> body) {
    RealmEntity realm = requireRealm(realmName);
    String displayName = stringField(body, "displayName");
    if (displayName == null || displayName.isBlank()) {
      return scimError(400, "displayName is required");
    }
    boolean exists = !em.createQuery(
            "select g from ScimGroupEntity g where g.realm.id = :rid and g.displayName = :dn",
            ScimGroupEntity.class)
        .setParameter("rid", realm.getId()).setParameter("dn", displayName)
        .setMaxResults(1).getResultList().isEmpty();
    if (exists) return Response.status(409).entity(scimErrorBody(409, "Group already exists")).build();

    ScimGroupEntity group = new ScimGroupEntity();
    group.setId(UUID.randomUUID());
    group.setRealm(realm);
    group.setExternalId(stringField(body, "externalId"));
    group.setDisplayName(displayName);
    group.setProvisionedAt(java.time.OffsetDateTime.now());
    em.persist(group);

    persistGroupMembers(group, body);
    return Response.status(201).entity(toScimGroup(group, realmName)).build();
  }

  // ── Groups — get ──────────────────────────────────────────────────────────

  @GET
  @Path("/Groups/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Get SCIM group by ID")
  public Response getGroup(
      @PathParam("realm") String realmName,
      @PathParam("id") UUID id) {
    requireRealm(realmName);
    ScimGroupEntity group = em.find(ScimGroupEntity.class, id);
    if (group == null) return scimError(404, "Group not found");
    return Response.ok(toScimGroup(group, realmName)).build();
  }

  // ── Groups — replace ──────────────────────────────────────────────────────

  @PUT
  @Path("/Groups/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({"application/scim+json", MediaType.APPLICATION_JSON})
  @Transactional
  @Operation(summary = "Replace SCIM group (full update)")
  public Response replaceGroup(
      @PathParam("realm") String realmName,
      @PathParam("id") UUID id,
      Map<String, Object> body) {
    requireRealm(realmName);
    ScimGroupEntity group = em.find(ScimGroupEntity.class, id);
    if (group == null) return scimError(404, "Group not found");
    String displayName = stringField(body, "displayName");
    if (displayName == null || displayName.isBlank()) {
      return scimError(400, "displayName is required");
    }
    if (groupNameExists(group.getRealm(), displayName, group.getId())) {
      return Response.status(409).entity(scimErrorBody(409, "Group already exists")).build();
    }
    group.setExternalId(stringField(body, "externalId"));
    group.setDisplayName(displayName);
    group.setLastSyncedAt(OffsetDateTime.now());
    em.merge(group);
    replaceGroupMembers(group, body.get("members"));
    return Response.ok(toScimGroup(group, realmName)).build();
  }

  @PATCH
  @Path("/Groups/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({"application/scim+json", MediaType.APPLICATION_JSON})
  @Transactional
  @Operation(summary = "Patch SCIM group")
  public Response patchGroup(
      @PathParam("realm") String realmName,
      @PathParam("id") UUID id,
      Map<String, Object> body) {
    requireRealm(realmName);
    ScimGroupEntity group = em.find(ScimGroupEntity.class, id);
    if (group == null) return scimError(404, "Group not found");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> operations = (List<Map<String, Object>>) body.get("Operations");
    if (operations == null || operations.isEmpty()) {
      return scimError(400, "Operations is required");
    }
    try {
      for (Map<String, Object> op : operations) {
        applyGroupPatchOperation(group, op);
      }
    } catch (WebApplicationException e) {
      return scimWebApplicationError(e, "Invalid SCIM group patch request");
    }

    group.setLastSyncedAt(OffsetDateTime.now());
    em.merge(group);
    return Response.ok(toScimGroup(group, realmName)).build();
  }

  // ── Groups — delete ───────────────────────────────────────────────────────

  @DELETE
  @Path("/Groups/{id}")
  @Transactional
  @Operation(summary = "Delete SCIM group")
  public Response deleteGroup(
      @PathParam("realm") String realmName,
      @PathParam("id") UUID id) {
    requireRealm(realmName);
    ScimGroupEntity group = em.find(ScimGroupEntity.class, id);
    if (group == null) return scimError(404, "Group not found");
    em.remove(group);
    return Response.noContent().build();
  }

  // ── Internal helpers ───────────────────────────────────────────────────────

  private Map<String, Object> executeBulkOperation(
      String realmName,
      Map<String, Object> operation,
      Map<String, UUID> bulkIdReferences) {
    String method = stringField(operation, "method");
    String rawPath = stringField(operation, "path");
    String bulkId = stringField(operation, "bulkId");
    if (method == null || method.isBlank()) {
      return bulkResult(null, bulkId, null, 400, scimErrorBody(400, "Bulk operation method is required"));
    }
    if (rawPath == null || rawPath.isBlank()) {
      return bulkResult(method, bulkId, null, 400, scimErrorBody(400, "Bulk operation path is required"));
    }

    try {
      String normalizedPath = normalizeBulkPath(realmName, rawPath);
      String resolvedPath = resolveBulkPathReferences(normalizedPath, bulkIdReferences);
      List<String> segments = bulkPathSegments(resolvedPath);
      if (segments.isEmpty() || segments.size() > 2) {
        return bulkResult(method, bulkId, null, 400, scimErrorBody(400, "Unsupported bulk path"));
      }

      String resource = canonicalBulkResource(segments.get(0));
      UUID resourceId = segments.size() == 2 ? parseUuid(segments.get(1), "Invalid SCIM resource id") : null;
      Map<String, Object> resolvedData = resolveBulkData(operation.get("data"), bulkIdReferences);
      Response response = invokeBulkOperation(realmName, method, resource, resourceId, resolvedData);
      return bulkResultFromResponse(realmName, method, bulkId, resource, resourceId, response, bulkIdReferences);
    } catch (WebApplicationException e) {
      int status = e.getResponse() != null ? e.getResponse().getStatus() : 400;
      Object entity = e.getResponse() != null ? e.getResponse().getEntity() : null;
      return bulkResult(method, bulkId, null, status, entity == null ? scimErrorBody(status, e.getMessage()) : entity);
    } catch (IllegalArgumentException e) {
      return bulkResult(method, bulkId, null, 400, scimErrorBody(400, e.getMessage()));
    } catch (Exception e) {
      return bulkResult(method, bulkId, null, 500, scimErrorBody(500, "Bulk operation failed"));
    }
  }

  private RealmEntity requireRealm(String realmName) {
    RealmEntity r = getRealmEntity(realmName);
    if (r == null) throw new NotFoundException("Realm not found");
    return r;
  }

  private RealmEntity getRealmEntity(String realmName) {
    return em.createQuery(
            "select r from RealmEntity r where r.name = :n and r.enabled = true", RealmEntity.class)
        .setParameter("n", realmName)
        .getResultStream().findFirst().orElse(null);
  }

  private ScimUserEntity findByUserName(RealmEntity realm, String userName) {
    return em.createQuery(
            "select s from ScimUserEntity s where s.realm.id = :rid and s.userName = :un",
            ScimUserEntity.class)
        .setParameter("rid", realm.getId())
        .setParameter("un", userName)
        .getResultStream().findFirst().orElse(null);
  }

  private boolean userNameExists(RealmEntity realm, String userName, UUID excludeId) {
    ScimUserEntity existing = findByUserName(realm, userName);
    return existing != null && (excludeId == null || !existing.getId().equals(excludeId));
  }

  private boolean groupNameExists(RealmEntity realm, String displayName, UUID excludeId) {
    ScimGroupEntity existing = em.createQuery(
            "select g from ScimGroupEntity g where g.realm.id = :rid and g.displayName = :dn",
            ScimGroupEntity.class)
        .setParameter("rid", realm.getId())
        .setParameter("dn", displayName)
        .setMaxResults(1)
        .getResultStream()
        .findFirst()
        .orElse(null);
    return existing != null && (excludeId == null || !existing.getId().equals(excludeId));
  }

  private <T> List<T> paginate(List<T> items, int startIndex, int count) {
    int safeCount = Math.max(0, Math.min(count, DEFAULT_COUNT));
    int start = Math.max(0, startIndex - 1);
    if (safeCount == 0 || start >= items.size()) {
      return List.of();
    }
    return items.subList(start, Math.min(items.size(), start + safeCount));
  }

  private boolean matchesUserFilter(ScimUserEntity user, String filter) {
    ParsedFilter parsed = parseFilter(filter);
    if (parsed == null) {
      return true;
    }
    return matchesFilterValue(userFilterValue(user, parsed.attribute()), parsed);
  }

  private boolean matchesGroupFilter(ScimGroupEntity group, String filter) {
    ParsedFilter parsed = parseFilter(filter);
    if (parsed == null) {
      return true;
    }
    return matchesFilterValue(groupFilterValue(group, parsed.attribute()), parsed);
  }

  private ParsedFilter parseFilter(String filter) {
    if (filter == null || filter.isBlank()) {
      return null;
    }
    java.util.regex.Matcher mat = java.util.regex.Pattern
        .compile("([A-Za-z][A-Za-z0-9.]+)\\s+(eq|co)\\s+\"([^\"]+)\"", java.util.regex.Pattern.CASE_INSENSITIVE)
        .matcher(filter.trim());
    if (!mat.matches()) {
      throw new BadRequestException("Unsupported SCIM filter");
    }
    return new ParsedFilter(mat.group(1), mat.group(2), mat.group(3));
  }

  private String userFilterValue(ScimUserEntity user, String attribute) {
    return switch (normalizeAttribute(attribute)) {
      case "username" -> user.getUserName();
      case "displayname" -> user.getDisplayName();
      case "externalid" -> user.getExternalId();
      case "emails.value", "email" -> user.getEmail();
      case "name.givenname" -> user.getGivenName();
      case "name.familyname" -> user.getFamilyName();
      default -> throw new BadRequestException("Unsupported SCIM user filter attribute: " + attribute);
    };
  }

  private String groupFilterValue(ScimGroupEntity group, String attribute) {
    return switch (normalizeAttribute(attribute)) {
      case "displayname" -> group.getDisplayName();
      case "externalid" -> group.getExternalId();
      default -> throw new BadRequestException("Unsupported SCIM group filter attribute: " + attribute);
    };
  }

  private boolean matchesFilterValue(String actual, ParsedFilter filter) {
    if (actual == null) {
      return false;
    }
    return switch (filter.operator().toLowerCase(Locale.ROOT)) {
      case "eq" -> actual.equalsIgnoreCase(filter.value());
      case "co" -> actual.toLowerCase(Locale.ROOT).contains(filter.value().toLowerCase(Locale.ROOT));
      default -> false;
    };
  }

  private String normalizeAttribute(String attribute) {
    return attribute.trim().toLowerCase(Locale.ROOT);
  }

  private record ParsedFilter(String attribute, String operator, String value) {}

  private Map<String, Object> toScimUser(ScimUserEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("schemas",     List.of(SCIM_USER_SCHEMA));
    m.put("id",          e.getId().toString());
    if (e.getExternalId() != null) m.put("externalId", e.getExternalId());
    m.put("userName",    e.getUserName());
    if (e.getDisplayName() != null) m.put("displayName", e.getDisplayName());
    Map<String, Object> name = new LinkedHashMap<>();
    if (e.getGivenName()  != null) name.put("givenName",  e.getGivenName());
    if (e.getFamilyName() != null) name.put("familyName", e.getFamilyName());
    if (!name.isEmpty()) m.put("name", name);
    if (e.getEmail() != null) {
      m.put("emails", List.of(Map.of("value", e.getEmail(), "primary", true, "type", "work")));
    }
    m.put("active", e.getActive());
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("resourceType", "User");
    meta.put("created", formatDate(e.getProvisionedAt()));
    if (e.getLastSyncedAt() != null) meta.put("lastModified", formatDate(e.getLastSyncedAt()));
    m.put("meta", meta);
    return m;
  }

  private String formatDate(OffsetDateTime dt) {
    return dt == null ? null : dt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  private String stringField(Map<String, Object> m, String key) {
    Object v = m.get(key);
    return v instanceof String s ? s.trim() : null;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> resolveBulkData(Object data, Map<String, UUID> bulkIdReferences) {
    if (data == null) {
      return null;
    }
    if (!(data instanceof Map<?, ?> map)) {
      throw new BadRequestException("Bulk operation data must be an object");
    }
    return (Map<String, Object>) resolveBulkReferences(map, bulkIdReferences);
  }

  private Object resolveBulkReferences(Object value, Map<String, UUID> bulkIdReferences) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        copy.put(String.valueOf(entry.getKey()), resolveBulkReferences(entry.getValue(), bulkIdReferences));
      }
      return copy;
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>(list.size());
      for (Object item : list) {
        copy.add(resolveBulkReferences(item, bulkIdReferences));
      }
      return copy;
    }
    if (value instanceof String text) {
      return resolveBulkReferenceValue(text, bulkIdReferences);
    }
    return value;
  }

  private String resolveBulkReferenceValue(String value, Map<String, UUID> bulkIdReferences) {
    if (!value.startsWith("bulkId:")) {
      return value;
    }
    String reference = value.substring("bulkId:".length()).trim();
    UUID resolved = bulkIdReferences.get(reference);
    if (resolved == null) {
      throw new BadRequestException("Unknown bulkId reference: " + reference);
    }
    return resolved.toString();
  }

  private String normalizeBulkPath(String realmName, String rawPath) {
    String normalized = rawPath.trim();
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    String prefix = "scim/v2/realms/" + realmName + "/";
    if (normalized.regionMatches(true, 0, prefix, 0, prefix.length())) {
      normalized = normalized.substring(prefix.length());
    }
    return normalized;
  }

  private String resolveBulkPathReferences(String path, Map<String, UUID> bulkIdReferences) {
    StringBuilder resolved = new StringBuilder();
    String[] segments = path.split("/");
    for (int i = 0; i < segments.length; i++) {
      String segment = segments[i];
      if (segment.startsWith("bulkId:")) {
        segment = resolveBulkReferenceValue(segment, bulkIdReferences);
      }
      if (resolved.length() > 0) {
        resolved.append('/');
      }
      resolved.append(segment);
    }
    return resolved.toString();
  }

  private List<String> bulkPathSegments(String path) {
    List<String> segments = new ArrayList<>();
    for (String segment : path.split("/")) {
      if (!segment.isBlank()) {
        segments.add(segment);
      }
    }
    return segments;
  }

  private String canonicalBulkResource(String resource) {
    if ("Users".equalsIgnoreCase(resource)) {
      return "Users";
    }
    if ("Groups".equalsIgnoreCase(resource)) {
      return "Groups";
    }
    throw new BadRequestException("Unsupported bulk resource: " + resource);
  }

  private UUID parseUuid(String value, String errorMessage) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(errorMessage);
    }
  }

  private Response invokeBulkOperation(
      String realmName,
      String method,
      String resource,
      UUID resourceId,
      Map<String, Object> data) {
    String normalizedMethod = method.toUpperCase(Locale.ROOT);
    return switch (resource) {
      case "Users" -> switch (normalizedMethod) {
        case "POST" -> createUser(realmName, requireBulkData(data, normalizedMethod, resource));
        case "PUT" -> replaceUser(realmName, requireResourceId(resourceId, resource), requireBulkData(data, normalizedMethod, resource));
        case "PATCH" -> patchUser(realmName, requireResourceId(resourceId, resource), requireBulkData(data, normalizedMethod, resource));
        case "DELETE" -> deleteUser(realmName, requireResourceId(resourceId, resource));
        default -> throw new BadRequestException("Unsupported bulk method for Users: " + method);
      };
      case "Groups" -> switch (normalizedMethod) {
        case "POST" -> createGroup(realmName, requireBulkData(data, normalizedMethod, resource));
        case "PUT" -> replaceGroup(realmName, requireResourceId(resourceId, resource), requireBulkData(data, normalizedMethod, resource));
        case "PATCH" -> patchGroup(realmName, requireResourceId(resourceId, resource), requireBulkData(data, normalizedMethod, resource));
        case "DELETE" -> deleteGroup(realmName, requireResourceId(resourceId, resource));
        default -> throw new BadRequestException("Unsupported bulk method for Groups: " + method);
      };
      default -> throw new BadRequestException("Unsupported bulk resource: " + resource);
    };
  }

  private UUID requireResourceId(UUID resourceId, String resource) {
    if (resourceId == null) {
      throw new BadRequestException(resource + " bulk operation requires an id path");
    }
    return resourceId;
  }

  private Map<String, Object> requireBulkData(
      Map<String, Object> data,
      String method,
      String resource) {
    if (data == null) {
      throw new BadRequestException("Bulk " + method + " " + resource + " operation requires data");
    }
    return data;
  }

  private Map<String, Object> bulkResultFromResponse(
      String realmName,
      String method,
      String bulkId,
      String resource,
      UUID resourceId,
      Response response,
      Map<String, UUID> bulkIdReferences) {
    int status = response.getStatus();
    Object entity = response.getEntity();
    String location = bulkLocation(realmName, resource, resourceId, entity);
    if (status < 400 && bulkId != null && entity instanceof Map<?, ?> map) {
      Object idValue = map.get("id");
      if (idValue instanceof String idText) {
        bulkIdReferences.put(bulkId, parseUuid(idText, "Invalid bulk response id"));
      }
    }
    return bulkResult(method, bulkId, location, status, entity);
  }

  private String bulkLocation(
      String realmName,
      String resource,
      UUID resourceId,
      Object entity) {
    UUID id = resourceId;
    if (id == null && entity instanceof Map<?, ?> map) {
      Object idValue = map.get("id");
      if (idValue instanceof String idText) {
        id = parseUuid(idText, "Invalid bulk response id");
      }
    }
    if (id == null) {
      return null;
    }
    return "/scim/v2/realms/" + realmName + "/" + resource + "/" + id;
  }

  private Map<String, Object> bulkResult(
      String method,
      String bulkId,
      String location,
      int status,
      Object entity) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (method != null && !method.isBlank()) {
      result.put("method", method.toUpperCase(Locale.ROOT));
    }
    if (bulkId != null && !bulkId.isBlank()) {
      result.put("bulkId", bulkId);
    }
    if (location != null && !location.isBlank()) {
      result.put("location", location);
    }
    result.put("status", String.valueOf(status));
    if (entity != null) {
      result.put("response", entity);
    }
    return result;
  }

  private Integer intField(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Integer.parseInt(text.trim());
      } catch (NumberFormatException e) {
        throw new BadRequestException("Expected integer value");
      }
    }
    return null;
  }

  private void applyUserPatchOperation(ScimUserEntity entity, Map<String, Object> op) {
    String opType = stringField(op, "op");
    String path = stringField(op, "path");
    Object value = op.get("value");
    if (opType == null || opType.isBlank()) {
      throw new BadRequestException("SCIM patch op is required");
    }
    switch (opType.toLowerCase(Locale.ROOT)) {
      case "add", "replace" -> applyUserPatchValue(entity, path, value);
      case "remove" -> removeUserPatchValue(entity, path, value);
      default -> throw new BadRequestException("Unsupported SCIM user patch op: " + opType);
    }
  }

  private void applyUserPatchValue(ScimUserEntity entity, String path, Object value) {
    if (path == null || path.isBlank()) {
      if (!(value instanceof Map<?, ?> map)) {
        throw new BadRequestException("SCIM user patch value object is required when path is omitted");
      }
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        applyUserPatchValue(entity, String.valueOf(entry.getKey()), entry.getValue());
      }
      return;
    }
    switch (normalizeAttribute(path)) {
      case "username" -> {
        String userName = requireNonBlankString(value, "userName is required");
        if (userNameExists(entity.getRealm(), userName, entity.getId())) {
          throw conflict("User already exists");
        }
        entity.setUserName(userName);
      }
      case "displayname" -> entity.setDisplayName(stringValue(value));
      case "externalid" -> entity.setExternalId(stringValue(value));
      case "name.givenname" -> entity.setGivenName(stringValue(value));
      case "name.familyname" -> entity.setFamilyName(stringValue(value));
      case "emails", "emails.value" -> entity.setEmail(emailValue(value));
      case "active" -> entity.setActive(booleanValue(value, "active must be boolean"));
      default -> throw new BadRequestException("Unsupported SCIM user patch path: " + path);
    }
  }

  private void removeUserPatchValue(ScimUserEntity entity, String path, Object value) {
    if (path == null || path.isBlank()) {
      if (!(value instanceof Map<?, ?> map)) {
        throw new BadRequestException("SCIM user remove patch requires a path or object value");
      }
      for (Object key : map.keySet()) {
        removeUserPatchValue(entity, String.valueOf(key), map.get(key));
      }
      return;
    }
    switch (normalizeAttribute(path)) {
      case "displayname" -> entity.setDisplayName(null);
      case "externalid" -> entity.setExternalId(null);
      case "name.givenname" -> entity.setGivenName(null);
      case "name.familyname" -> entity.setFamilyName(null);
      case "emails", "emails.value" -> entity.setEmail(null);
      case "active" -> entity.setActive(Boolean.FALSE);
      case "username" -> throw new BadRequestException("userName cannot be removed");
      default -> throw new BadRequestException("Unsupported SCIM user patch path: " + path);
    }
  }

  private void applyGroupPatchOperation(ScimGroupEntity group, Map<String, Object> op) {
    String opType = stringField(op, "op");
    String path = stringField(op, "path");
    Object value = op.get("value");
    if (opType == null || opType.isBlank()) {
      throw new BadRequestException("SCIM patch op is required");
    }
    switch (opType.toLowerCase(Locale.ROOT)) {
      case "add" -> applyGroupPatchValue(group, path, value, false);
      case "replace" -> applyGroupPatchValue(group, path, value, true);
      case "remove" -> removeGroupPatchValue(group, path, value);
      default -> throw new BadRequestException("Unsupported SCIM group patch op: " + opType);
    }
  }

  private void applyGroupPatchValue(ScimGroupEntity group, String path, Object value, boolean replaceMembers) {
    if (path == null || path.isBlank()) {
      if (!(value instanceof Map<?, ?> map)) {
        throw new BadRequestException("SCIM group patch value object is required when path is omitted");
      }
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        applyGroupPatchValue(group, String.valueOf(entry.getKey()), entry.getValue(), replaceMembers);
      }
      return;
    }
    switch (normalizeAttribute(path)) {
      case "displayname" -> {
        String displayName = requireNonBlankString(value, "displayName is required");
        if (groupNameExists(group.getRealm(), displayName, group.getId())) {
          throw conflict("Group already exists");
        }
        group.setDisplayName(displayName);
      }
      case "externalid" -> group.setExternalId(stringValue(value));
      case "members" -> {
        if (replaceMembers) {
          replaceGroupMembers(group, value);
        } else {
          addGroupMembers(group, value);
        }
      }
      default -> throw new BadRequestException("Unsupported SCIM group patch path: " + path);
    }
  }

  private void removeGroupPatchValue(ScimGroupEntity group, String path, Object value) {
    if (path == null || path.isBlank()) {
      if (!(value instanceof Map<?, ?> map)) {
        throw new BadRequestException("SCIM group remove patch requires a path or object value");
      }
      for (Object key : map.keySet()) {
        removeGroupPatchValue(group, String.valueOf(key), map.get(key));
      }
      return;
    }

    String filteredMemberId = extractGroupMemberFilterValue(path);
    if (filteredMemberId != null) {
      removeGroupMembers(group, List.of(filteredMemberId));
      return;
    }

    switch (normalizeAttribute(path)) {
      case "externalid" -> group.setExternalId(null);
      case "members" -> {
        if (value == null) {
          clearGroupMembers(group);
        } else {
          removeGroupMembers(group, value);
        }
      }
      case "displayname" -> throw new BadRequestException("displayName cannot be removed");
      default -> throw new BadRequestException("Unsupported SCIM group patch path: " + path);
    }
  }

  private String extractGroupMemberFilterValue(String path) {
    java.util.regex.Matcher matcher = java.util.regex.Pattern
        .compile("members\\s*\\[\\s*value\\s+eq\\s+\"([^\"]+)\"\\s*\\]", java.util.regex.Pattern.CASE_INSENSITIVE)
        .matcher(path.trim());
    return matcher.matches() ? matcher.group(1) : null;
  }

  private String stringValue(Object value) {
    return value instanceof String text ? text.trim() : null;
  }

  private String requireNonBlankString(Object value, String errorMessage) {
    String text = stringValue(value);
    if (text == null || text.isBlank()) {
      throw new BadRequestException(errorMessage);
    }
    return text;
  }

  private Boolean booleanValue(Object value, String errorMessage) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof String text && !text.isBlank()) {
      if ("true".equalsIgnoreCase(text)) {
        return Boolean.TRUE;
      }
      if ("false".equalsIgnoreCase(text)) {
        return Boolean.FALSE;
      }
    }
    throw new BadRequestException(errorMessage);
  }

  @SuppressWarnings("unchecked")
  private String emailValue(Object value) {
    if (value instanceof String text) {
      return text.trim();
    }
    if (value instanceof Map<?, ?> map) {
      Object nested = map.get("value");
      return nested instanceof String text ? text.trim() : null;
    }
    if (value instanceof List<?> list && !list.isEmpty()) {
      return emailValue(list.get(0));
    }
    return null;
  }

  private WebApplicationException conflict(String detail) {
    return new WebApplicationException(Response.status(409).entity(scimErrorBody(409, detail)).build());
  }

  private String nameField(Map<String, Object> m, String key) {
    Object nameObj = m.get("name");
    if (nameObj instanceof Map<?,?> nameMap) {
      Object v = nameMap.get(key);
      return v instanceof String s ? s.trim() : null;
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private String emailField(Map<String, Object> m) {
    Object emails = m.get("emails");
    if (emails instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?,?> e) {
      Object v = e.get("value");
      return v instanceof String s ? s.trim() : null;
    }
    return null;
  }

  private boolean boolField(Map<String, Object> m, String key, boolean def) {
    Object v = m.get(key);
    if (v instanceof Boolean b) return b;
    return def;
  }

  private Response scimError(int status, String detail) {
    return Response.status(status).entity(scimErrorBody(status, detail)).build();
  }

  private Response scimWebApplicationError(WebApplicationException e, String fallbackDetail) {
    int status = e.getResponse() != null ? e.getResponse().getStatus() : 400;
    if (e.getResponse() != null && e.getResponse().hasEntity()) {
      return e.getResponse();
    }
    String detail = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : fallbackDetail;
    return Response.status(status).entity(scimErrorBody(status, detail)).build();
  }

  private Map<String, Object> scimErrorBody(int status, String detail) {
    return Map.of("schemas", List.of(SCIM_ERROR), "status", String.valueOf(status), "detail", detail);
  }

  private Map<String, Object> attr(String name, String type, boolean required, String k, String v) {
    return Map.of("name", name, "type", type, "required", required, k, v);
  }

  private void persistGroupMembers(ScimGroupEntity group, Map<String, Object> body) {
    persistGroupMembers(group, body.get("members"));
  }

  private void persistGroupMembers(ScimGroupEntity group, Object membersObj) {
    for (UUID userId : memberIds(membersObj)) {
      ScimUserEntity user = em.find(ScimUserEntity.class, userId);
      if (user == null) continue;
      boolean alreadyMember = !em.createQuery(
              "select m from ScimGroupMemberEntity m where m.group.id = :gid and m.user.id = :uid",
              ScimGroupMemberEntity.class)
          .setParameter("gid", group.getId())
          .setParameter("uid", userId)
          .setMaxResults(1)
          .getResultList()
          .isEmpty();
      if (alreadyMember) continue;
      ScimGroupMemberEntity member = new ScimGroupMemberEntity();
      member.setId(UUID.randomUUID());
      member.setGroup(group);
      member.setUser(user);
      em.persist(member);
    }
  }

  private void addGroupMembers(ScimGroupEntity group, Object membersObj) {
    persistGroupMembers(group, membersObj);
  }

  private void replaceGroupMembers(ScimGroupEntity group, Object membersObj) {
    clearGroupMembers(group);
    persistGroupMembers(group, membersObj);
  }

  private void removeGroupMembers(ScimGroupEntity group, Object membersObj) {
    for (UUID userId : memberIds(membersObj)) {
      em.createQuery("delete from ScimGroupMemberEntity m where m.group.id = :gid and m.user.id = :uid")
          .setParameter("gid", group.getId())
          .setParameter("uid", userId)
          .executeUpdate();
    }
  }

  private void clearGroupMembers(ScimGroupEntity group) {
    em.createQuery("delete from ScimGroupMemberEntity m where m.group.id = :gid")
        .setParameter("gid", group.getId())
        .executeUpdate();
  }

  private List<UUID> memberIds(Object membersObj) {
    if (membersObj == null) {
      return List.of();
    }
    List<?> members = membersObj instanceof List<?> list ? list : List.of(membersObj);
    List<UUID> ids = new ArrayList<>();
    for (Object member : members) {
      UUID userId = memberId(member);
      if (userId != null && !ids.contains(userId)) {
        ids.add(userId);
      }
    }
    return ids;
  }

  private UUID memberId(Object member) {
    if (member instanceof String text) {
      try {
        return UUID.fromString(text);
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
    if (member instanceof Map<?, ?> memberMap) {
      Object valueObj = memberMap.get("value");
      if (valueObj instanceof String text) {
        try {
          return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
          return null;
        }
      }
    }
    return null;
  }

  private Map<String, Object> toScimGroup(ScimGroupEntity g, String realmName) {
    List<ScimGroupMemberEntity> members = em.createQuery(
            "select m from ScimGroupMemberEntity m where m.group.id = :gid",
            ScimGroupMemberEntity.class)
        .setParameter("gid", g.getId()).getResultList();
    List<Map<String, Object>> memberRefs = members.stream().map(m -> {
      Map<String, Object> ref = new LinkedHashMap<>();
      ref.put("value", m.getUser().getId().toString());
      ref.put("$ref", "/scim/v2/realms/" + realmName + "/Users/" + m.getUser().getId());
      return ref;
    }).collect(java.util.stream.Collectors.toList());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("schemas",     List.of(SCIM_GROUP_SCHEMA));
    result.put("id",          g.getId().toString());
    if (g.getExternalId() != null) result.put("externalId", g.getExternalId());
    result.put("displayName", g.getDisplayName());
    result.put("members",     memberRefs);
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("resourceType", "Group");
    meta.put("created", formatDate(g.getProvisionedAt()));
    if (g.getLastSyncedAt() != null) meta.put("lastModified", formatDate(g.getLastSyncedAt()));
    result.put("meta", meta);
    return result;
  }
}
