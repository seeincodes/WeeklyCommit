package com.acme.weeklycommit.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /plans/{id}/reviews}.
 *
 * <p>Comment is optional (a manager can acknowledge silently). The cap is generous because plain
 * text is the only safe content (no markdown, no HTML); 5000 chars is enough for a thoughtful
 * paragraph without being a comment-novel attack vector.
 */
public record CreateReviewRequest(@Size(max = 5000) String comment) {}
