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
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

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

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiErrorEnvelope> handleMissingParam(
      MissingServletRequestParameterException ex) {
    return status(
        HttpStatus.BAD_REQUEST,
        ApiErrorEnvelope.of(
            "VALIDATION_FAILED",
            "Missing required parameter: " + ex.getParameterName(),
            List.of(new FieldError(ex.getParameterName(), "is required"))));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorEnvelope> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex) {
    // Malformed UUID, bad date format, etc. Don't leak the actual failed value into the
    // response message — just flag the field by name.
    return status(
        HttpStatus.BAD_REQUEST,
        ApiErrorEnvelope.of(
            "VALIDATION_FAILED",
            "Parameter '" + ex.getName() + "' is the wrong type",
            List.of(new FieldError(ex.getName(), "invalid format"))));
  }

  @ExceptionHandler(PageSizeExceededException.class)
  public ResponseEntity<ApiErrorEnvelope> handlePageSizeExceeded(PageSizeExceededException ex) {
    return status(
        HttpStatus.BAD_REQUEST,
        ApiErrorEnvelope.of(
            "VALIDATION_FAILED",
            ex.getMessage(),
            List.of(new FieldError("size", "max " + ex.getMaxSize()))));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiErrorEnvelope> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException ex) {
    return status(
        HttpStatus.METHOD_NOT_ALLOWED,
        ApiErrorEnvelope.of("METHOD_NOT_ALLOWED", "HTTP method not supported"));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiErrorEnvelope> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException ex) {
    return status(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        ApiErrorEnvelope.of("UNSUPPORTED_MEDIA_TYPE", "Content-Type not supported"));
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ApiErrorEnvelope> handleNoHandler(NoHandlerFoundException ex) {
    return status(HttpStatus.NOT_FOUND, ApiErrorEnvelope.of("NOT_FOUND", "No handler for path"));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiErrorEnvelope> handleIllegalState(IllegalStateException ex) {
    // Invariant violations (e.g., JWT missing required claim, RECONCILED plan with null
    // reconciledAt). Not a client-fixable problem — surface as 500 so ops notices, log stack.
    log.error("Invariant violation in request pipeline", ex);
    return status(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ApiErrorEnvelope.of("INTERNAL_ERROR", "An unexpected error occurred"));
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
