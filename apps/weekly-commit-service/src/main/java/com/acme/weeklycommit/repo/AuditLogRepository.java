package com.acme.weeklycommit.repo;

import com.acme.weeklycommit.domain.entity.AuditLog;
import com.acme.weeklycommit.domain.enums.AuditEntityType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

  List<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
      AuditEntityType entityType, UUID entityId);
}
