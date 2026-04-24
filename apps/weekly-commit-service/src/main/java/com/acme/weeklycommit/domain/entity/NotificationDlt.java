package com.acme.weeklycommit.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Durable record of a failed notification send. Written when Resilience4j retries are exhausted or
 * the circuit is open (MEMO decision #2). Deleted by admin replay on success.
 *
 * <p>Not extending {@link AbstractAuditingEntity} — this table is populated by background retry
 * paths, not by user activity; standard audit fields would be misleading.
 */
@Entity
@Table(name = "notification_dlt")
public class NotificationDlt {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @NotNull
  @Size(max = 50)
  @Column(name = "event_type", nullable = false, length = 50)
  private String eventType;

  @NotNull
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private JsonNode payload;

  @NotNull
  @Column(name = "last_error", nullable = false, columnDefinition = "TEXT")
  private String lastError;

  @NotNull
  @Column(name = "attempts", nullable = false)
  private int attempts;

  @NotNull
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected NotificationDlt() {
    // JPA
  }

  public NotificationDlt(
      UUID id, String eventType, JsonNode payload, String lastError, int attempts) {
    this.id = id;
    this.eventType = eventType;
    this.payload = payload;
    this.lastError = lastError;
    this.attempts = attempts;
  }

  public UUID getId() {
    return id;
  }

  public String getEventType() {
    return eventType;
  }

  public JsonNode getPayload() {
    return payload;
  }

  public String getLastError() {
    return lastError;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
