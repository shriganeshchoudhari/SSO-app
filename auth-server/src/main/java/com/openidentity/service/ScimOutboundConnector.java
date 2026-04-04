package com.openidentity.service;

import com.openidentity.domain.ScimOutboundTargetEntity;
import java.util.Map;

public interface ScimOutboundConnector {
  record UpsertResult(boolean created, String remoteId) {}

  UpsertResult upsertUser(
      ScimOutboundTargetEntity target, Map<String, Object> scimUser, String bearerToken);

  UpsertResult upsertGroup(
      ScimOutboundTargetEntity target, Map<String, Object> scimGroup, String bearerToken);

  boolean deleteUser(
      ScimOutboundTargetEntity target, String remoteUserId, String externalId, String bearerToken);

  boolean deleteGroup(
      ScimOutboundTargetEntity target, String remoteGroupId, String externalId, String bearerToken);
}
