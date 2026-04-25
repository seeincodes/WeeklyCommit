package com.acme.weeklycommit.api.dto;

import com.acme.weeklycommit.domain.enums.AuditEntityType;
import com.acme.weeklycommit.domain.enums.AuditEventType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * API-layer view of an {@link com.acme.weeklycommit.domain.entity.AuditLog} row. Includes the JSONB
 * metadata blob unchanged — it carries notification ack ids and error context that the IcDrawer
 * surfaces inline.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AuditLogResponse(
    UUID id,
    AuditEntityType entityType,
    UUID entityId,
    AuditEventType eventType,
    UUID actorId,
    String fromState,
    String toState,
    JsonNode metadata,
    Instant occurredAt) {}
