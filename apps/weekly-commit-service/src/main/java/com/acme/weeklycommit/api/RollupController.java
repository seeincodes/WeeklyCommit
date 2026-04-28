package com.acme.weeklycommit.api;

import com.acme.weeklycommit.api.dto.ApiEnvelope;
import com.acme.weeklycommit.api.dto.RollupResponse;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.service.RollupService;
import io.micrometer.core.annotation.Timed;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager team rollup. Single endpoint per USER_FLOW.md: aggregates a week's plans for a manager
 * into the dashboard payload (alignment %, completion %, tier distribution, unreviewed count, stuck
 * commit count, per-member cards with flags).
 */
@RestController
@RequestMapping("/api/v1/rollup")
public class RollupController {

  private final RollupService rollupService;

  public RollupController(RollupService rollupService) {
    this.rollupService = rollupService;
  }

  @Timed(
      value = "wc.rollup.get_team",
      description = "GET /rollup/team — manager dashboard rollup (PRD p95 < 500ms target)",
      histogram = true,
      percentiles = {0.5, 0.95, 0.99})
  @GetMapping("/team")
  public ResponseEntity<ApiEnvelope<RollupResponse>> getTeamRollup(
      @RequestParam("managerId") UUID managerId,
      @RequestParam("weekStart") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
      AuthenticatedPrincipal caller) {
    RollupResponse rollup = rollupService.computeRollup(managerId, weekStart, caller);
    return ResponseEntity.ok(ApiEnvelope.of(rollup));
  }
}
