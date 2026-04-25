package com.acme.weeklycommit.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.weeklycommit.domain.entity.ManagerReview;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ManagerReviewMapperTest {

  private final ManagerReviewMapper mapper = Mappers.getMapper(ManagerReviewMapper.class);

  @Test
  void toResponse_copiesAllFieldsFromFullyPopulatedEntity() {
    UUID id = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    Instant ack = Instant.parse("2026-05-02T09:00:00Z");

    ManagerReview entity = new ManagerReview(id, planId, managerId, ack);
    entity.setComment("good week, ship it");

    ManagerReviewResponse resp = mapper.toResponse(entity);

    assertThat(resp.id()).isEqualTo(id);
    assertThat(resp.planId()).isEqualTo(planId);
    assertThat(resp.managerId()).isEqualTo(managerId);
    assertThat(resp.acknowledgedAt()).isEqualTo(ack);
    assertThat(resp.comment()).isEqualTo("good week, ship it");
  }

  @Test
  void toResponse_nullComment_passesThrough() {
    // Manager can acknowledge without leaving a comment; null must propagate, not get
    // substituted with "" or a placeholder.
    ManagerReview entity =
        new ManagerReview(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-05-02T09:00:00Z"));
    // comment intentionally not set

    ManagerReviewResponse resp = mapper.toResponse(entity);

    assertThat(resp.comment()).isNull();
  }

  @Test
  void toResponse_nullEntity_returnsNull() {
    // Defensive: null-in / null-out, no NPE, no empty DTO.
    assertThat(mapper.toResponse(null)).isNull();
  }
}
