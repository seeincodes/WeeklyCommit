package com.acme.weeklycommit.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standard success envelope for every REST response. Matches the contract documented in {@code
 * docs/USER_FLOW.md#api-endpoints}.
 *
 * <p>Error responses use {@link ApiErrorEnvelope}; never the success shape with null data.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(T data, Map<String, Object> meta) {

  public static <T> ApiEnvelope<T> of(T data) {
    return new ApiEnvelope<>(data, baseMeta());
  }

  public static <T> ApiEnvelope<T> of(T data, Map<String, Object> extraMeta) {
    Map<String, Object> meta = baseMeta();
    meta.putAll(extraMeta);
    return new ApiEnvelope<>(data, meta);
  }

  private static Map<String, Object> baseMeta() {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("now", Instant.now().toString());
    return meta;
  }
}
