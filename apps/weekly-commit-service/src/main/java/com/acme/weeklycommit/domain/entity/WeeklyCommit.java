package com.acme.weeklycommit.domain.entity;

import com.acme.weeklycommit.domain.enums.ActualStatus;
import com.acme.weeklycommit.domain.enums.ChessTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Child of {@link WeeklyPlan}. 1:1 link to a Supporting Outcome (MEMO decision #8). No optimistic
 * locking (MEMO decision #4 — last-write-wins on commit level). Carry-forward self-references are
 * stored by id rather than a bidirectional @ManyToOne, to keep the walk cap-friendly (52 hops).
 */
@Entity
@Table(name = "weekly_commit")
public class WeeklyCommit extends AbstractAuditingEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @NotNull
  @Column(name = "plan_id", nullable = false, updatable = false)
  private UUID planId;

  @NotNull
  @Size(max = 200)
  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @NotNull
  @Column(name = "supporting_outcome_id", nullable = false)
  private UUID supportingOutcomeId;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "chess_tier", nullable = false, length = 8)
  private ChessTier chessTier;

  @NotNull
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "category_tags", nullable = false, columnDefinition = "text[]")
  private String[] categoryTags = new String[0];

  @Column(name = "estimated_hours", precision = 4, scale = 1)
  private BigDecimal estimatedHours;

  @NotNull
  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Size(max = 200)
  @Column(name = "related_meeting", length = 200)
  private String relatedMeeting;

  @Column(name = "carried_forward_from_id")
  private UUID carriedForwardFromId;

  @Column(name = "carried_forward_to_id")
  private UUID carriedForwardToId;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "actual_status", nullable = false, length = 8)
  private ActualStatus actualStatus = ActualStatus.PENDING;

  @Column(name = "actual_note", columnDefinition = "TEXT")
  private String actualNote;

  protected WeeklyCommit() {
    // JPA
  }

  public WeeklyCommit(
      UUID id, UUID planId, String title, UUID supportingOutcomeId, ChessTier chessTier,
      int displayOrder) {
    this.id = id;
    this.planId = planId;
    this.title = title;
    this.supportingOutcomeId = supportingOutcomeId;
    this.chessTier = chessTier;
    this.displayOrder = displayOrder;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPlanId() {
    return planId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public UUID getSupportingOutcomeId() {
    return supportingOutcomeId;
  }

  public void setSupportingOutcomeId(UUID supportingOutcomeId) {
    this.supportingOutcomeId = supportingOutcomeId;
  }

  public ChessTier getChessTier() {
    return chessTier;
  }

  public void setChessTier(ChessTier chessTier) {
    this.chessTier = chessTier;
  }

  public String[] getCategoryTags() {
    // Defensive copy — category_tags may be mutated by mappers.
    return Arrays.copyOf(categoryTags, categoryTags.length);
  }

  public void setCategoryTags(String[] tags) {
    this.categoryTags = (tags == null) ? new String[0] : Arrays.copyOf(tags, tags.length);
  }

  public BigDecimal getEstimatedHours() {
    return estimatedHours;
  }

  public void setEstimatedHours(BigDecimal estimatedHours) {
    this.estimatedHours = estimatedHours;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(int displayOrder) {
    this.displayOrder = displayOrder;
  }

  public String getRelatedMeeting() {
    return relatedMeeting;
  }

  public void setRelatedMeeting(String relatedMeeting) {
    this.relatedMeeting = relatedMeeting;
  }

  public UUID getCarriedForwardFromId() {
    return carriedForwardFromId;
  }

  public void setCarriedForwardFromId(UUID carriedForwardFromId) {
    this.carriedForwardFromId = carriedForwardFromId;
  }

  public UUID getCarriedForwardToId() {
    return carriedForwardToId;
  }

  public void setCarriedForwardToId(UUID carriedForwardToId) {
    this.carriedForwardToId = carriedForwardToId;
  }

  public ActualStatus getActualStatus() {
    return actualStatus;
  }

  public void setActualStatus(ActualStatus actualStatus) {
    this.actualStatus = actualStatus;
  }

  public String getActualNote() {
    return actualNote;
  }

  public void setActualNote(String actualNote) {
    this.actualNote = actualNote;
  }
}
