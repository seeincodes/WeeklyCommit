package com.acme.weeklycommit.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Admin-only view of an unassigned employee (manager_id IS NULL). Deliberately narrow: exposes only
 * the fields ops needs to assign a manager. Internal projection fields (active, lastSyncedAt
 * exposed for staleness diagnostics, org_id never — caller's own org by construction) stay close to
 * the entity.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UnassignedEmployeeResponse(UUID id, String displayName, Instant lastSyncedAt) {}
