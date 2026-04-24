package com.acme.weeklycommit.repo;

import com.acme.weeklycommit.domain.entity.NotificationDlt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationDltRepository extends JpaRepository<NotificationDlt, UUID> {

  /**
   * Metric query for the CloudWatch "DLT &lt; 1h" alarm. Caller passes the cutoff Instant
   * (application time, not NOW()).
   */
  @Query("SELECT COUNT(d) FROM NotificationDlt d WHERE d.createdAt >= :since")
  long countCreatedSince(@Param("since") Instant since);

  List<NotificationDlt> findByEventTypeOrderByCreatedAtAsc(String eventType);
}
