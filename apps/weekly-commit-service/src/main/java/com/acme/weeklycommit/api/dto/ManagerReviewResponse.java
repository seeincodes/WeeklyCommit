package com.acme.weeklycommit.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** API-layer view of a {@code ManagerReview}. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ManagerReviewResponse(
    UUID id, UUID planId, UUID managerId, String comment, Instant acknowledgedAt) {}
