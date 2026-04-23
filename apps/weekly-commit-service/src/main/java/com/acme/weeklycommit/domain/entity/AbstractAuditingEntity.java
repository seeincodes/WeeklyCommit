package com.acme.weeklycommit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base class for every entity we own. Brief requires this.
 *
 * <p>{@code createdBy} and {@code lastModifiedBy} hold the employee UUID (as string) from the
 * auditing JWT. System-triggered writes (scheduled jobs with no principal) record {@code
 * "system"}; see {@link com.acme.weeklycommit.config.JpaAuditingConfig}.
 *
 * <p>Timestamp fields map to {@code TIMESTAMPTZ} columns; always UTC per MEMO.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AbstractAuditingEntity implements Serializable {

  @CreatedBy
  @Column(name = "created_by", nullable = false, updatable = false, length = 64)
  private String createdBy;

  @CreatedDate
  @Column(name = "created_date", nullable = false, updatable = false)
  private Instant createdDate;

  @LastModifiedBy
  @Column(name = "last_modified_by", nullable = false, length = 64)
  private String lastModifiedBy;

  @LastModifiedDate
  @Column(name = "last_modified_date", nullable = false)
  private Instant lastModifiedDate;

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedDate() {
    return createdDate;
  }

  public String getLastModifiedBy() {
    return lastModifiedBy;
  }

  public Instant getLastModifiedDate() {
    return lastModifiedDate;
  }
}
