package com.acme.weeklycommit.api.exception;

/**
 * Thrown when a paged endpoint receives a {@code size} parameter above the server cap (100 per
 * USER_FLOW.md). Maps to HTTP 400 {@code VALIDATION_FAILED} in {@link GlobalExceptionHandler}.
 *
 * <p>Separate type rather than generic {@code IllegalArgumentException} so the handler can
 * surface the exact cap in the error message without guessing.
 */
public class PageSizeExceededException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  private final int requestedSize;
  private final int maxSize;

  public PageSizeExceededException(int requestedSize, int maxSize) {
    super("Page size " + requestedSize + " exceeds maximum of " + maxSize);
    this.requestedSize = requestedSize;
    this.maxSize = maxSize;
  }

  public int getRequestedSize() {
    return requestedSize;
  }

  public int getMaxSize() {
    return maxSize;
  }
}
