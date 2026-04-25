package com.acme.weeklycommit.api.dto;

import com.acme.weeklycommit.domain.enums.ChessTier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /plans/{planId}/commits}.
 *
 * <p>Required: {@code title}, {@code supportingOutcomeId}, {@code chessTier} per presearch §3
 * invariants. {@code displayOrder} is optional — server assigns next-in-list when null.
 */
public record CreateCommitRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 5000) String description,
    @NotNull UUID supportingOutcomeId,
    @NotNull ChessTier chessTier,
    @Valid List<@Size(max = 50) String> categoryTags,
    @DecimalMin("0.0") @DecimalMax("99.9") BigDecimal estimatedHours,
    Integer displayOrder,
    @Size(max = 200) String relatedMeeting) {}
