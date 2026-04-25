package com.acme.weeklycommit.integration.rcdo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Service-layer view of an RCDO Supporting Outcome. Contains everything our backend needs to render
 * an inline breadcrumb without a second fetch — see ADR-0001.
 *
 * <p>{@link Breadcrumb} mirrors the upstream JSON shape exactly. Unknown fields are tolerated so
 * the contract can grow at the upstream without breaking our deserializer.
 */
public record SupportingOutcomeView(UUID id, String label, boolean active, Breadcrumb breadcrumb) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Breadcrumb(
      Node rallyCry, Node definingObjective, Node coreOutcome, Node supportingOutcome) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Node(UUID id, String label) {}
}
