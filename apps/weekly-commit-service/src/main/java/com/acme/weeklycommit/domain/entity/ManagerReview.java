package com.acme.weeklycommit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per manager acknowledgement of a plan. Appendable on RECONCILED plans; {@link
 * WeeklyPlan#getManagerReviewedAt()} is set to the latest {@code acknowledgedAt} as a side-effect
 * of creating a review.
 */
@Entity
@Table(name = "manager_review")
public class ManagerReview extends AbstractAuditingEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @NotNull
  @Column(name = "plan_id", nullable = false, updatable = false)
  private UUID planId;

  @NotNull
  @Column(name = "manager_id", nullable = false, updatable = false)
  private UUID managerId;

  @Column(name = "comment", columnDefinition = "TEXT")
  private String comment;

  @NotNull
  @Column(name = "acknowledged_at", nullable = false)
  private Instant acknowledgedAt;

  protected ManagerReview() {
    // JPA
  }

  public ManagerReview(UUID id, UUID planId, UUID managerId, Instant acknowledgedAt) {
    this.id = id;
    this.planId = planId;
    this.managerId = managerId;
    this.acknowledgedAt = acknowledgedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPlanId() {
    return planId;
  }

  public UUID getManagerId() {
    return managerId;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public Instant getAcknowledgedAt() {
    return acknowledgedAt;
  }
}
