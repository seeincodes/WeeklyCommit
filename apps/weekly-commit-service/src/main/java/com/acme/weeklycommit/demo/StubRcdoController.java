package com.acme.weeklycommit.demo;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * In-process stub of the upstream RCDO service. Active only under the {@code demo} profile;
 * production deploys hit the real RCDO host configured via {@code RCDO_BASE_URL}.
 *
 * <p>Why a controller and not a swap-in stub bean: the existing {@link
 * com.acme.weeklycommit.integration.rcdo.RcdoClient} is wired through a {@code WebClient} with a
 * configurable base URL. Pointing that base URL at {@code http://localhost:8080} (the same backend)
 * and serving these paths from this controller is a 12-line stub that requires zero refactor of the
 * client. The localhost loopback is a few-millisecond cost on every picker render, acceptable for a
 * demo with one user clicking through.
 *
 * <p>Data shape mirrors the wiremock mappings under {@code apps/weekly-commit-ui/wiremock/} so the
 * RCDO picker renders the same labels in the demo as in standalone-dev mode. Each outcome belongs
 * to {@code orgId = 11111111-1111-1111-1111-111111111111}, the canonical demo org id from {@link
 * DemoDataSeeder} and the cypress test users.
 */
@RestController
@RequestMapping("/rcdo")
@Profile("demo")
public class StubRcdoController {

  /**
   * Hardcoded outcome catalog. Five Supporting Outcomes spanning two Rally Cries so the picker has
   * interesting breadcrumb structure to render. UUIDs follow the same convention as the wiremock
   * seed file: {@code aaaaaaaX-1111-1111-1111-aaaaaaaaaaaa}.
   */
  private static final List<Map<String, Object>> OUTCOMES =
      List.of(
          outcome(
              "aaaaaaa1-1111-1111-1111-aaaaaaaaaaaa",
              "Alignment tooling GA",
              "Unblock product-led growth",
              "Product-led GTM",
              "Tooling readiness"),
          outcome(
              "aaaaaaa2-1111-1111-1111-aaaaaaaaaaaa",
              "Self-serve activation",
              "Unblock product-led growth",
              "Product-led GTM",
              "Activation rate"),
          outcome(
              "aaaaaaa3-1111-1111-1111-aaaaaaaaaaaa",
              "Onboarding A/B test",
              "Unblock product-led growth",
              "Product-led GTM",
              "Activation rate"),
          outcome(
              "bbbbbbb1-2222-2222-2222-bbbbbbbbbbbb",
              "Hire 4 senior engineers",
              "Build the team",
              "Org capacity",
              "Senior engineering hires"),
          outcome(
              "bbbbbbb2-2222-2222-2222-bbbbbbbbbbbb",
              "Onboarding kit refresh",
              "Build the team",
              "Org capacity",
              "Time-to-first-PR"));

  @GetMapping("/supporting-outcomes")
  public ResponseEntity<Map<String, Object>> list() {
    // Real RCDO supports `?orgId=...&active=true` query params; we ignore them in
    // the stub and return everything because the demo only has one org.
    return ResponseEntity.ok(Map.of("data", OUTCOMES));
  }

  @GetMapping("/supporting-outcomes/{id}")
  public ResponseEntity<Map<String, Object>> hydrate(@PathVariable("id") UUID id) {
    return OUTCOMES.stream()
        .filter(o -> id.toString().equals(o.get("id")))
        .findFirst()
        .<ResponseEntity<Map<String, Object>>>map(
            o -> ResponseEntity.ok(Map.<String, Object>of("data", o)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private static Map<String, Object> outcome(
      String id,
      String label,
      String rallyCryLabel,
      String definingObjLabel,
      String coreOutcomeLabel) {
    // Stable UUIDs per RallyCry/DO/CO so the breadcrumbs render consistently across
    // calls. The actual values don't matter to the UI -- it only displays labels --
    // but keeping them stable makes manual cross-checking easier.
    return Map.of(
        "id",
        id,
        "label",
        label,
        "active",
        true,
        "breadcrumb",
        Map.of(
            "rallyCry",
                Map.of("id", "11111111-2222-3333-4444-555555555551", "label", rallyCryLabel),
            "definingObjective",
                Map.of("id", "11111111-2222-3333-4444-555555555552", "label", definingObjLabel),
            "coreOutcome",
                Map.of("id", "11111111-2222-3333-4444-555555555553", "label", coreOutcomeLabel),
            "supportingOutcome", Map.of("id", id, "label", label)));
  }
}
