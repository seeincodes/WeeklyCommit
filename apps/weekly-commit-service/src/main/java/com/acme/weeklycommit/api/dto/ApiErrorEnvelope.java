package com.acme.weeklycommit.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Error response shape. {@code code} is a stable machine-readable string (documented in
 * USER_FLOW.md); {@code message} is a short human-readable explanation. {@code details} carries
 * per-field validation errors when applicable.
 *
 * <p>Never returned as the {@code data} field of a success envelope.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorEnvelope(ApiError error, Map<String, Object> meta) {

  public static ApiErrorEnvelope of(String code, String message) {
    return new ApiErrorEnvelope(
        new ApiError(code, message, null), Map.of("now", Instant.now().toString()));
  }

  public static ApiErrorEnvelope of(String code, String message, List<FieldError> details) {
    return new ApiErrorEnvelope(
        new ApiError(code, message, details), Map.of("now", Instant.now().toString()));
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ApiError(String code, String message, List<FieldError> details) {}

  public record FieldError(String field, String message) {}
}
