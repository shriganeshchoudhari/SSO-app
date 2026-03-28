package com.openidentity.service;

import com.openidentity.domain.ScimOutboundTargetEntity;
import java.util.Map;

public interface ScimOutboundConnector {
  record UpsertResult(boolean created, String remoteId) {}

  UpsertResult upsertUser(
      ScimOutboundTargetEntity target, Map<String, Object> scimUser, String bearerToken);
}
