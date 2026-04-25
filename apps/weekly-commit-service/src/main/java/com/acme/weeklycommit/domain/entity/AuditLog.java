package com.acme.weeklycommit.domain.entity;

import com.acme.weeklycommit.domain.enums.AuditEntityType;
import com.acme.weeklycommit.domain.enums.AuditEventType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only audit log. Written by state-machine transitions and manager-review creations. Access
 * governed by {@code GET /api/v1/audit/plans/{id}} (MANAGER role or self; see docs/USER_FLOW.md).
 *
 * <p>No FK on {@code entityId} — see V5 migration note.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 32)
  private AuditEntityType entityType;

  @NotNull
  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 32)
  private AuditEventType eventType;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "from_state", length = 16)
  private String fromState;

  @Column(name = "to_state", length = 16)
  private String toState;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private JsonNode metadata;

  @NotNull
  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt = Instant.now();

  protected AuditLog() {
    // JPA
  }

  public AuditLog(
      UUID id, AuditEntityType entityType, UUID entityId, AuditEventType eventType, UUID actorId) {
    this.id = id;
    this.entityType = entityType;
    this.entityId = entityId;
    this.eventType = eventType;
    this.actorId = actorId;
  }

  public UUID getId() {
    return id;
  }

  public AuditEntityType getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public AuditEventType getEventType() {
    return eventType;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getFromState() {
    return fromState;
  }

  public void setFromState(String fromState) {
    this.fromState = fromState;
  }

  public String getToState() {
    return toState;
  }

  public void setToState(String toState) {
    this.toState = toState;
  }

  public JsonNode getMetadata() {
    return metadata;
  }

  public void setMetadata(JsonNode metadata) {
    this.metadata = metadata;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
