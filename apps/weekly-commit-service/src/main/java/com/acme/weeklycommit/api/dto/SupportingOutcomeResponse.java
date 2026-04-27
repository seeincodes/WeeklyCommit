package com.acme.weeklycommit.api.dto;

import com.acme.weeklycommit.integration.rcdo.SupportingOutcomeView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * UI-facing pass-through view of an RCDO Supporting Outcome. Mirrors the {@code SupportingOutcome}
 * type in {@code libs/rtk-api-client/src/rcdo.ts} -- the picker contract -- so the JSON shape
 * matches the TypeScript type one-for-one.
 *
 * <p>This response intentionally does not echo any field beyond the picker's needs (no upstream
 * timestamps, no owner ids). Adding fields later is forward-compatible -- tightening would not be.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SupportingOutcomeResponse(
    UUID id, String label, boolean active, Breadcrumb breadcrumb) {

  public record Breadcrumb(
      Level rallyCry, Level definingObjective, Level coreOutcome, Level supportingOutcome) {}

  public record Level(UUID id, String label) {}

  /**
   * Lift an integration-layer {@link SupportingOutcomeView} into the UI-facing pass-through. Same
   * fields, distinct types -- keeps the integration layer free to evolve (add internal-only fields,
   * version the upstream contract) without leaking into the API.
   */
  public static SupportingOutcomeResponse from(SupportingOutcomeView v) {
    return new SupportingOutcomeResponse(
        v.id(),
        v.label(),
        v.active(),
        new Breadcrumb(
            level(v.breadcrumb().rallyCry()),
            level(v.breadcrumb().definingObjective()),
            level(v.breadcrumb().coreOutcome()),
            level(v.breadcrumb().supportingOutcome())));
  }

  private static Level level(SupportingOutcomeView.Node node) {
    return new Level(node.id(), node.label());
  }
}
