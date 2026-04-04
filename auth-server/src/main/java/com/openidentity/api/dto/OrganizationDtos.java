package com.openidentity.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class OrganizationDtos {

  public static class CreateOrganizationRequest {
    public String name;
    public String displayName;
    public String logoText;
    public String primaryColor;
    public String accentColor;
    public String locale;
    public Boolean enabled;
  }

  public static class UpdateOrganizationRequest {
    public String displayName;
    public String logoText;
    public String primaryColor;
    public String accentColor;
    public String locale;
    public Boolean enabled;
  }

  public static class OrganizationResponse {
    public UUID id;
    public UUID realmId;
    public String name;
    public String displayName;
    public String logoText;
    public String primaryColor;
    public String accentColor;
    public String locale;
    public Boolean enabled;
    public OffsetDateTime createdAt;

    public OrganizationResponse() {}
    public OrganizationResponse(UUID id, UUID realmId, String name, String displayName,
        String logoText, String primaryColor, String accentColor, String locale,
        Boolean enabled, OffsetDateTime createdAt) {
      this.id = id;
      this.realmId = realmId;
      this.name = name;
      this.displayName = displayName;
      this.logoText = logoText;
      this.primaryColor = primaryColor;
      this.accentColor = accentColor;
      this.locale = locale;
      this.enabled = enabled;
      this.createdAt = createdAt;
    }
  }

  public static class AddMemberRequest {
    public UUID userId;
    /** "member" or "admin" */
    public String orgRole = "member";
  }

  public static class MemberResponse {
    public UUID id;
    public UUID organizationId;
    public UUID userId;
    public String orgRole;
    public OffsetDateTime joinedAt;

    public MemberResponse() {}
    public MemberResponse(UUID id, UUID organizationId, UUID userId, String orgRole,
        OffsetDateTime joinedAt) {
      this.id = id;
      this.organizationId = organizationId;
      this.userId = userId;
      this.orgRole = orgRole;
      this.joinedAt = joinedAt;
    }
  }
}
