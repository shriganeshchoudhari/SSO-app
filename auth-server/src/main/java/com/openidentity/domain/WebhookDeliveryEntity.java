package com.openidentity.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_delivery")
public class WebhookDeliveryEntity {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "realm_id", nullable = false)
  private RealmEntity realm;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "endpoint_id", nullable = false)
  private WebhookEndpointEntity endpoint;

  @Column(name = "event_category", nullable = false, length = 64)
  private String eventCategory;

  @Column(name = "event_type", nullable = false, length = 255)
  private String eventType;

  @Column(name = "event_id", length = 255)
  private String eventId;

  @Lob
  @Basic(fetch = FetchType.LAZY)
  @Column(name = "request_body", nullable = false)
  private String requestBody;

  @Column(name = "response_status")
  private Integer responseStatus;

  @Lob
  @Basic(fetch = FetchType.LAZY)
  @Column(name = "response_body")
  private String responseBody;

  @Lob
  @Basic(fetch = FetchType.LAZY)
  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "success", nullable = false)
  private Boolean success;

  @Column(name = "attempted_at", nullable = false)
  private OffsetDateTime attemptedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public RealmEntity getRealm() {
    return realm;
  }

  public void setRealm(RealmEntity realm) {
    this.realm = realm;
  }

  public WebhookEndpointEntity getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(WebhookEndpointEntity endpoint) {
    this.endpoint = endpoint;
  }

  public String getEventCategory() {
    return eventCategory;
  }

  public void setEventCategory(String eventCategory) {
    this.eventCategory = eventCategory;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getRequestBody() {
    return requestBody;
  }

  public void setRequestBody(String requestBody) {
    this.requestBody = requestBody;
  }

  public Integer getResponseStatus() {
    return responseStatus;
  }

  public void setResponseStatus(Integer responseStatus) {
    this.responseStatus = responseStatus;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public void setResponseBody(String responseBody) {
    this.responseBody = responseBody;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Boolean getSuccess() {
    return success;
  }

  public void setSuccess(Boolean success) {
    this.success = success;
  }

  public OffsetDateTime getAttemptedAt() {
    return attemptedAt;
  }

  public void setAttemptedAt(OffsetDateTime attemptedAt) {
    this.attemptedAt = attemptedAt;
  }

  public OffsetDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(OffsetDateTime completedAt) {
    this.completedAt = completedAt;
  }
}
