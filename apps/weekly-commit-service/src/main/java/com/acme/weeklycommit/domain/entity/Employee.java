package com.acme.weeklycommit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Projection of authoritative employee data from Auth0. Ownership is Auth0's; this row exists so
 * we can JOIN "who reports to whom" at query time. Kept deliberately small: the JWT already
 * carries employee_id, manager_id, and org_id for the calling user — this table is only for
 * *other* employees (team rollups, admin reports).
 *
 * <p>Null {@code managerId} is legitimate (unassigned); surfaces via {@code GET
 * /admin/unassigned-employees} per USER_FLOW.md.
 *
 * <p>No FK from {@code weekly_plan.employee_id} into this table — see V7 migration note. A sync
 * lag must not break plan writes.
 */
@Entity
@Table(name = "employee")
public class Employee extends AbstractAuditingEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "manager_id")
  private UUID managerId;

  @NotNull
  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Size(max = 200)
  @Column(name = "display_name", length = 200)
  private String displayName;

  @NotNull
  @Column(name = "active", nullable = false)
  private boolean active = true;

  @NotNull
  @Column(name = "last_synced_at", nullable = false)
  private Instant lastSyncedAt = Instant.now();

  protected Employee() {
    // JPA
  }

  public Employee(UUID id, UUID orgId) {
    this.id = id;
    this.orgId = orgId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getManagerId() {
    return managerId;
  }

  public void setManagerId(UUID managerId) {
    this.managerId = managerId;
  }

  public UUID getOrgId() {
    return orgId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Instant getLastSyncedAt() {
    return lastSyncedAt;
  }

  public void setLastSyncedAt(Instant lastSyncedAt) {
    this.lastSyncedAt = lastSyncedAt;
  }
}
