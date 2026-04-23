package com.acme.weeklycommit.api.exception;

import com.acme.weeklycommit.api.dto.ApiErrorEnvelope;
import com.acme.weeklycommit.api.dto.ApiErrorEnvelope.FieldError;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Central mapping from exception types to HTTP status + {@link ApiErrorEnvelope}. Referenced from
 * USER_FLOW.md example-queries table and CLAUDE.md error-logging rules.
 *
 * <p>Mapping matrix:
 *
 * <ul>
 *   <li>{@link MethodArgumentNotValidException}, {@link ConstraintViolationException}, {@link
 *       HttpMessageNotReadableException} → 400 {@code VALIDATION_FAILED}
 *   <li>{@link AuthenticationException}, {@link AuthenticationCredentialsNotFoundException} → 401
 *       {@code UNAUTHORIZED}
 *   <li>{@link AccessDeniedException} → 403 {@code FORBIDDEN}
 *   <li>{@link ResourceNotFoundException} → 404 {@code NOT_FOUND}
 *   <li>{@link OptimisticLockException}, {@link OptimisticLockingFailureException} → 409 {@code
 *       CONFLICT_OPTIMISTIC_LOCK}
 *   <li>{@link InvalidStateTransitionException} → 422 {@code INVALID_STATE_TRANSITION}
 *   <li>Uncaught {@link Throwable} → 500 {@code INTERNAL_ERROR} (logged with stack trace; message
 *       scrubbed)
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorEnvelope> handleValidation(MethodArgumentNotValidException ex) {
    List<FieldError> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
    return status(
        HttpStatus.BAD_REQUEST,
        ApiErrorEnvelope.of("VALIDATION_FAILED", "Request validation failed", details));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiErrorEnvelope> handleConstraintViolation(
      ConstraintViolationException ex) {
    List<FieldError> details =
        ex.getConstraintViolations().stream()
            .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
    return status(
        HttpStatus.BAD_REQUEST,
        ApiErrorEnvelope.of("VALIDATION_FAILED", "Request validation failed", details));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorEnvelope> handleUnreadable(HttpMessageNotReadableException ex) {
    return status(
        HttpStatus.BAD_REQUEST,
        ApiErrorEnvelope.of("VALIDATION_FAILED", "Request body is malformed"));
  }

  @ExceptionHandler({
    AuthenticationException.class,
    AuthenticationCredentialsNotFoundException.class
  })
  public ResponseEntity<ApiErrorEnvelope> handleAuth(Exception ex) {
    return status(
        HttpStatus.UNAUTHORIZED, ApiErrorEnvelope.of("UNAUTHORIZED", "Authentication required"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiErrorEnvelope> handleForbidden(AccessDeniedException ex) {
    return status(
        HttpStatus.FORBIDDEN,
        ApiErrorEnvelope.of("FORBIDDEN", "You do not have access to this resource"));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiErrorEnvelope> handleNotFound(ResourceNotFoundException ex) {
    return status(HttpStatus.NOT_FOUND, ApiErrorEnvelope.of("NOT_FOUND", ex.getMessage()));
  }

  @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
  public ResponseEntity<ApiErrorEnvelope> handleOptimisticLock(Exception ex) {
    // UI global middleware catches 409 and refetches + toasts (see USER_FLOW.md).
    return status(
        HttpStatus.CONFLICT,
        ApiErrorEnvelope.of(
            "CONFLICT_OPTIMISTIC_LOCK",
            "The resource was modified concurrently; refetch and retry"));
  }

  @ExceptionHandler(InvalidStateTransitionException.class)
  public ResponseEntity<ApiErrorEnvelope> handleInvalidTransition(
      InvalidStateTransitionException ex) {
    return status(
        HttpStatus.UNPROCESSABLE_ENTITY,
        new ApiErrorEnvelope(
            new ApiErrorEnvelope.ApiError("INVALID_STATE_TRANSITION", ex.getMessage(), null),
            Map.of(
                "now",
                java.time.Instant.now().toString(),
                "fromState",
                ex.getFromState(),
                "toState",
                ex.getToState())));
  }

  @ExceptionHandler(Throwable.class)
  public ResponseEntity<ApiErrorEnvelope> handleUnexpected(Throwable ex) {
    // Never leak messages; log the full stack trace for the on-call.
    log.error("Unhandled exception in request pipeline", ex);
    return status(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ApiErrorEnvelope.of("INTERNAL_ERROR", "An unexpected error occurred"));
  }

  private static ResponseEntity<ApiErrorEnvelope> status(HttpStatus status, ApiErrorEnvelope body) {
    return ResponseEntity.status(status).body(body);
  }
}
