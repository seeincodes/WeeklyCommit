package com.acme.weeklycommit.repo;

import com.acme.weeklycommit.domain.entity.ManagerReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManagerReviewRepository extends JpaRepository<ManagerReview, UUID> {

  List<ManagerReview> findByPlanIdOrderByAcknowledgedAtAsc(UUID planId);
}
