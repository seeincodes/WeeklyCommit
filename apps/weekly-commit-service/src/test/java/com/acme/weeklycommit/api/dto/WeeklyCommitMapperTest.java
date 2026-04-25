package com.acme.weeklycommit.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.domain.enums.ActualStatus;
import com.acme.weeklycommit.domain.enums.ChessTier;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class WeeklyCommitMapperTest {

  private final WeeklyCommitMapper mapper = Mappers.getMapper(WeeklyCommitMapper.class);

  @Test
  void toResponse_copiesAllFieldsAndEmbedsDerived() {
    UUID commitId = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    UUID outcomeId = UUID.randomUUID();
    UUID carriedFromId = UUID.randomUUID();

    WeeklyCommit entity = new WeeklyCommit(commitId, planId, "ship picker", outcomeId, ChessTier.ROCK, 0);
    entity.setDescription("spike + RCDO integration");
    entity.setEstimatedHours(new BigDecimal("4.5"));
    entity.setCategoryTags(new String[] {"spike", "infra"});
    entity.setRelatedMeeting("Tues 10am sync");
    entity.setCarriedForwardFromId(carriedFromId);
    entity.setActualStatus(ActualStatus.DONE);
    entity.setActualNote("landed green Friday");

    WeeklyCommitResponse resp = mapper.toResponse(entity, 3, true);

    assertThat(resp.id()).isEqualTo(commitId);
    assertThat(resp.planId()).isEqualTo(planId);
    assertThat(resp.title()).isEqualTo("ship picker");
    assertThat(resp.description()).isEqualTo("spike + RCDO integration");
    assertThat(resp.supportingOutcomeId()).isEqualTo(outcomeId);
    assertThat(resp.chessTier()).isEqualTo(ChessTier.ROCK);
    assertThat(resp.categoryTags()).containsExactly("spike", "infra");
    assertThat(resp.estimatedHours()).isEqualByComparingTo("4.5");
    assertThat(resp.displayOrder()).isEqualTo(0);
    assertThat(resp.relatedMeeting()).isEqualTo("Tues 10am sync");
    assertThat(resp.carriedForwardFromId()).isEqualTo(carriedFromId);
    assertThat(resp.carriedForwardToId()).isNull();
    assertThat(resp.actualStatus()).isEqualTo(ActualStatus.DONE);
    assertThat(resp.actualNote()).isEqualTo("landed green Friday");

    assertThat(resp.derived()).isNotNull();
    assertThat(resp.derived().carryStreak()).isEqualTo(3);
    assertThat(resp.derived().stuckFlag()).isTrue();
  }

  @Test
  void toResponse_freshCommit_nullsPassThrough() {
    WeeklyCommit fresh =
        new WeeklyCommit(UUID.randomUUID(), UUID.randomUUID(), "t", UUID.randomUUID(), ChessTier.PEBBLE, 2);

    WeeklyCommitResponse resp = mapper.toResponse(fresh, 1, false);

    assertThat(resp.description()).isNull();
    assertThat(resp.estimatedHours()).isNull();
    assertThat(resp.categoryTags()).isEmpty();
    assertThat(resp.relatedMeeting()).isNull();
    assertThat(resp.carriedForwardFromId()).isNull();
    assertThat(resp.actualStatus()).isEqualTo(ActualStatus.PENDING);
    assertThat(resp.actualNote()).isNull();
    assertThat(resp.derived().carryStreak()).isEqualTo(1);
    assertThat(resp.derived().stuckFlag()).isFalse();
  }

  @Test
  void toResponse_nullEntity_returnsNull() {
    assertThat(mapper.toResponse(null, 0, false)).isNull();
  }
}
