package com.acme.weeklycommit.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.weeklycommit.api.dto.ApiErrorEnvelope;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void optimisticLockException_maps_to_409() {
    ResponseEntity<ApiErrorEnvelope> resp = handler.handleOptimisticLock(new OptimisticLockException("stale"));
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(resp.getBody()).isNotNull();
    assertThat(resp.getBody().error().code()).isEqualTo("CONFLICT_OPTIMISTIC_LOCK");
  }

  @Test
  void optimisticLockingFailureException_also_maps_to_409() {
    ResponseEntity<ApiErrorEnvelope> resp =
        handler.handleOptimisticLock(new OptimisticLockingFailureException("stale"));
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(resp.getBody().error().code()).isEqualTo("CONFLICT_OPTIMISTIC_LOCK");
  }

  @Test
  void invalidStateTransition_maps_to_422_and_reflects_states_in_meta() {
    ResponseEntity<ApiErrorEnvelope> resp =
        handler.handleInvalidTransition(
            new InvalidStateTransitionException("DRAFT", "RECONCILED", "not allowed"));
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(resp.getBody().error().code()).isEqualTo("INVALID_STATE_TRANSITION");
    assertThat(resp.getBody().meta())
        .containsEntry("fromState", "DRAFT")
        .containsEntry("toState", "RECONCILED");
  }

  @Test
  void accessDenied_maps_to_403() {
    ResponseEntity<ApiErrorEnvelope> resp =
        handler.handleForbidden(new AccessDeniedException("denied"));
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(resp.getBody().error().code()).isEqualTo("FORBIDDEN");
    // Real reason not leaked to client
    assertThat(resp.getBody().error().message()).doesNotContain("denied");
  }

  @Test
  void notFound_maps_to_404() {
    ResponseEntity<ApiErrorEnvelope> resp =
        handler.handleNotFound(new ResourceNotFoundException("WeeklyPlan", "abc-123"));
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(resp.getBody().error().code()).isEqualTo("NOT_FOUND");
  }

  @Test
  void unexpectedThrowable_maps_to_500_without_leaking_message() {
    ResponseEntity<ApiErrorEnvelope> resp =
        handler.handleUnexpected(new RuntimeException("sensitive-inner-detail"));
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(resp.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
    assertThat(resp.getBody().error().message()).doesNotContain("sensitive-inner-detail");
  }
}
