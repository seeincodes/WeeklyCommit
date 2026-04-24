package com.acme.weeklycommit.domain.entity;

import com.acme.weeklycommit.domain.enums.PlanState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Mutable JPA entity (not a record — Hibernate requires no-args ctor + setters). State and
 * lifecycle timestamps are mutated only via {@code WeeklyPlanStateMachine} (group 5); reflection
 * note is mutable in reconciliation mode per MEMO decision #5.
 */
@Entity
@Table(
    name = "weekly_plan",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_weekly_plan_employee_week",
            columnNames = {"employee_id", "week_start"}))
public class WeeklyPlan extends AbstractAuditingEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @NotNull
  @Column(name = "employee_id", nullable = false, updatable = false)
  private UUID employeeId;

  @NotNull
  @Column(name = "week_start", nullable = false, updatable = false)
  private LocalDate weekStart;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false, length = 16)
  private PlanState state = PlanState.DRAFT;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "reconciled_at")
  private Instant reconciledAt;

  @Column(name = "manager_reviewed_at")
  private Instant managerReviewedAt;

  @Size(max = 500)
  @Column(name = "reflection_note", length = 500)
  private String reflectionNote;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected WeeklyPlan() {
    // JPA
  }

  public WeeklyPlan(UUID id, UUID employeeId, LocalDate weekStart) {
    this.id = id;
    this.employeeId = employeeId;
    this.weekStart = weekStart;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEmployeeId() {
    return employeeId;
  }

  public LocalDate getWeekStart() {
    return weekStart;
  }

  public PlanState getState() {
    return state;
  }

  public void setState(PlanState state) {
    this.state = state;
  }

  public Instant getLockedAt() {
    return lockedAt;
  }

  public void setLockedAt(Instant lockedAt) {
    this.lockedAt = lockedAt;
  }

  public Instant getReconciledAt() {
    return reconciledAt;
  }

  public void setReconciledAt(Instant reconciledAt) {
    this.reconciledAt = reconciledAt;
  }

  public Instant getManagerReviewedAt() {
    return managerReviewedAt;
  }

  public void setManagerReviewedAt(Instant managerReviewedAt) {
    this.managerReviewedAt = managerReviewedAt;
  }

  public String getReflectionNote() {
    return reflectionNote;
  }

  public void setReflectionNote(String reflectionNote) {
    this.reflectionNote = reflectionNote;
  }

  public long getVersion() {
    return version;
  }
}
