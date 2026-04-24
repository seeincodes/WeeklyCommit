package com.acme.weeklycommit.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class WeeklyPlanMapperTest {

  private final WeeklyPlanMapper mapper = Mappers.getMapper(WeeklyPlanMapper.class);

  @Test
  void toResponse_copiesAllFieldsFromFullyPopulatedEntity() {
    UUID planId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();
    LocalDate weekStart = LocalDate.parse("2026-04-27");
    Instant lockedAt = Instant.parse("2026-04-27T17:00:00Z");
    Instant reconciledAt = Instant.parse("2026-05-01T15:30:00Z");
    Instant managerReviewedAt = Instant.parse("2026-05-02T09:00:00Z");

    WeeklyPlan entity = new WeeklyPlan(planId, employeeId, weekStart);
    entity.setState(PlanState.RECONCILED);
    entity.setLockedAt(lockedAt);
    entity.setReconciledAt(reconciledAt);
    entity.setManagerReviewedAt(managerReviewedAt);
    entity.setReflectionNote("good week");

    WeeklyPlanResponse resp = mapper.toResponse(entity);

    assertThat(resp.id()).isEqualTo(planId);
    assertThat(resp.employeeId()).isEqualTo(employeeId);
    assertThat(resp.weekStart()).isEqualTo(weekStart);
    assertThat(resp.state()).isEqualTo(PlanState.RECONCILED);
    assertThat(resp.lockedAt()).isEqualTo(lockedAt);
    assertThat(resp.reconciledAt()).isEqualTo(reconciledAt);
    assertThat(resp.managerReviewedAt()).isEqualTo(managerReviewedAt);
    assertThat(resp.reflectionNote()).isEqualTo("good week");
    assertThat(resp.version()).isEqualTo(entity.getVersion());
  }

  @Test
  void toResponse_nullsPassThroughFromFreshDraft() {
    // Null-safety matters — a fresh DRAFT carries nulls on every optional column and
    // must not trip the mapper (no NPE, no default-value substitution).
    WeeklyPlan fresh =
        new WeeklyPlan(UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2026-04-27"));

    WeeklyPlanResponse resp = mapper.toResponse(fresh);

    assertThat(resp.state()).isEqualTo(PlanState.DRAFT); // default on entity
    assertThat(resp.lockedAt()).isNull();
    assertThat(resp.reconciledAt()).isNull();
    assertThat(resp.managerReviewedAt()).isNull();
    assertThat(resp.reflectionNote()).isNull();
  }

  @Test
  void toResponse_nullEntity_returnsNull() {
    // Explicit null-in / null-out: callers should never pass null, but if they do, the
    // mapper should not mask the bug with an empty DTO.
    assertThat(mapper.toResponse(null)).isNull();
  }
}
