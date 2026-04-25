package com.acme.weeklycommit.api.dto;

import com.acme.weeklycommit.domain.enums.ActualStatus;
import com.acme.weeklycommit.domain.enums.ChessTier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code PATCH /commits/{id}}.
 *
 * <p><b>Partial update semantics</b>: non-null fields are applied; null fields are ignored (keep
 * current value). To clear an optional string field, send {@code ""} and the service coerces it
 * to {@code null} before save.
 *
 * <p><b>State-aware</b>: in DRAFT the service accepts the definition fields (title, description,
 * chessTier, etc.); in LOCKED past day-4 only {@code actualStatus} + {@code actualNote} are
 * accepted; any other state rejects the whole request. Supplying a non-null definition field
 * while the plan is in reconciliation mode also rejects — the client is trying to mutate an
 * immutable field for that state.
 */
public record UpdateCommitRequest(
    @Size(max = 200) String title,
    @Size(max = 5000) String description,
    UUID supportingOutcomeId,
    ChessTier chessTier,
    @Valid List<@Size(max = 50) String> categoryTags,
    @DecimalMin("0.0") @DecimalMax("99.9") BigDecimal estimatedHours,
    Integer displayOrder,
    @Size(max = 200) String relatedMeeting,
    ActualStatus actualStatus,
    @Size(max = 5000) String actualNote) {

  /** Any DRAFT-only field non-null? */
  public boolean touchesDefinitionFields() {
    return title != null
        || description != null
        || supportingOutcomeId != null
        || chessTier != null
        || categoryTags != null
        || estimatedHours != null
        || displayOrder != null
        || relatedMeeting != null;
  }

  /** Any reconciliation-mode field non-null? */
  public boolean touchesActualFields() {
    return actualStatus != null || actualNote != null;
  }
}
