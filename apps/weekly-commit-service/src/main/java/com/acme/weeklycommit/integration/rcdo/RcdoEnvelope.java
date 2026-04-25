package com.acme.weeklycommit.integration.rcdo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Internal deserialization shim for RCDO's {@code { data, meta }} response envelope. Generic in
 * {@code T} so we can reuse it for both single-outcome and list-of-outcomes endpoints. {@code meta}
 * is intentionally untyped — we don't consume any of its fields today, but tolerating it lets the
 * upstream evolve.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record RcdoEnvelope<T>(T data) {}
