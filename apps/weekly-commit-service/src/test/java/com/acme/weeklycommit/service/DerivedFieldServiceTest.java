package com.acme.weeklycommit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.domain.enums.ChessTier;
import com.acme.weeklycommit.repo.WeeklyCommitRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DerivedFieldServiceTest {

  @Mock private WeeklyCommitRepository commits;

  private DerivedFieldService service() {
    return new DerivedFieldService(commits);
  }

  // --- topRock ---

  @Test
  void topRock_noRocks_returnsEmpty() {
    UUID planId = UUID.randomUUID();
    when(commits.findTopRock(planId)).thenReturn(Optional.empty());

    assertThat(service().topRock(planId)).isEmpty();
  }

  @Test
  void topRock_returnsRepoResult() {
    UUID planId = UUID.randomUUID();
    WeeklyCommit rock =
        new WeeklyCommit(
            UUID.randomUUID(), planId, "land spike", UUID.randomUUID(), ChessTier.ROCK, 0);
    when(commits.findTopRock(planId)).thenReturn(Optional.of(rock));

    assertThat(service().topRock(planId)).contains(rock);
  }
}
