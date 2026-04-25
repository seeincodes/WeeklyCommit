package com.acme.weeklycommit.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /plans/{id}}.
 *
 * <p>Only the reflection note is mutable on a plan at this endpoint. {@code null} is accepted and
 * clears the note. 500-char cap mirrors the entity column + presearch §3 constraint.
 */
public record UpdateReflectionRequest(@Size(max = 500) String reflectionNote) {}
