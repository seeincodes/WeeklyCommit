package com.acme.weeklycommit.api;

import com.acme.weeklycommit.api.dto.ApiEnvelope;
import com.acme.weeklycommit.api.dto.CreateReviewRequest;
import com.acme.weeklycommit.api.dto.ManagerReviewMapper;
import com.acme.weeklycommit.api.dto.ManagerReviewResponse;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.ManagerReview;
import com.acme.weeklycommit.service.ManagerReviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface for {@link ManagerReview}. */
@RestController
@RequestMapping("/api/v1/plans")
public class ReviewsController {

  private final ManagerReviewService reviewService;
  private final ManagerReviewMapper mapper;

  public ReviewsController(ManagerReviewService reviewService, ManagerReviewMapper mapper) {
    this.reviewService = reviewService;
    this.mapper = mapper;
  }

  /**
   * Create a manager review on a RECONCILED plan. MANAGER-only. Side-effects (managerReviewedAt
   * + audit row) are owned by the service. Returns 201.
   */
  @PostMapping("/{planId}/reviews")
  public ResponseEntity<ApiEnvelope<ManagerReviewResponse>> createReview(
      @PathVariable UUID planId,
      @Valid @RequestBody CreateReviewRequest request,
      AuthenticatedPrincipal caller) {
    ManagerReview saved = reviewService.createReview(planId, request, caller);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiEnvelope.of(mapper.toResponse(saved)));
  }

  /** List reviews on a plan. Self-or-MANAGER. */
  @GetMapping("/{planId}/reviews")
  public ResponseEntity<ApiEnvelope<List<ManagerReviewResponse>>> listReviews(
      @PathVariable UUID planId, AuthenticatedPrincipal caller) {
    List<ManagerReviewResponse> body =
        reviewService.listReviews(planId, caller).stream().map(mapper::toResponse).toList();
    return ResponseEntity.ok(ApiEnvelope.of(body));
  }
}
