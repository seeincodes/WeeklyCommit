package com.acme.weeklycommit.api.exception;

/** Thrown by services when a required entity does not exist. Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  private final String resourceType;

  public ResourceNotFoundException(String resourceType, Object id) {
    super("%s not found: %s".formatted(resourceType, id));
    this.resourceType = resourceType;
  }

  public String getResourceType() {
    return resourceType;
  }
}
