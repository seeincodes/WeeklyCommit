package com.acme.weeklycommit.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.weeklycommit.api.dto.WeeklyCommitMapper;
import com.acme.weeklycommit.api.dto.WeeklyCommitResponse;
import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import com.acme.weeklycommit.domain.enums.ActualStatus;
import com.acme.weeklycommit.domain.enums.ChessTier;
import com.acme.weeklycommit.service.DerivedFieldService;
import com.acme.weeklycommit.service.WeeklyCommitService;
import com.acme.weeklycommit.testsupport.WebMvcTestConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CommitsController.class)
@Import(WebMvcTestConfig.class)
@ActiveProfiles("test")
class CommitsControllerTest {

  private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  @Autowired private MockMvc mvc;

  @MockBean private WeeklyCommitService commitService;
  @MockBean private DerivedFieldService derivedFieldService;
  @MockBean private WeeklyCommitMapper mapper;

  private static JwtRequestPostProcessor validJwt() {
    return jwt().jwt(b -> b.subject(EMPLOYEE_ID.toString()).claim("org_id", ORG_ID.toString()));
  }

  @Test
  void listCommits_200WithEnvelopeAndDerivedFields() throws Exception {
    UUID planId = UUID.randomUUID();
    WeeklyCommit c1 =
        new WeeklyCommit(UUID.randomUUID(), planId, "first", UUID.randomUUID(), ChessTier.ROCK, 0);
    WeeklyCommit c2 =
        new WeeklyCommit(
            UUID.randomUUID(), planId, "second", UUID.randomUUID(), ChessTier.PEBBLE, 1);

    when(commitService.findCommitsForPlan(any(), any())).thenReturn(List.of(c1, c2));
    when(derivedFieldService.deriveFor(c1.getId()))
        .thenReturn(new DerivedFieldService.Derived(3, true));
    when(derivedFieldService.deriveFor(c2.getId()))
        .thenReturn(new DerivedFieldService.Derived(1, false));
    when(mapper.toResponse(any(WeeklyCommit.class), anyInt(), anyBoolean()))
        .thenAnswer(
            inv -> {
              WeeklyCommit c = inv.getArgument(0);
              int streak = inv.getArgument(1);
              boolean stuck = inv.getArgument(2);
              return new WeeklyCommitResponse(
                  c.getId(),
                  c.getPlanId(),
                  c.getTitle(),
                  null,
                  c.getSupportingOutcomeId(),
                  c.getChessTier(),
                  List.of(),
                  null,
                  c.getDisplayOrder(),
                  null,
                  null,
                  null,
                  ActualStatus.PENDING,
                  null,
                  new WeeklyCommitResponse.Derived(streak, stuck));
            });

    mvc.perform(get("/api/v1/plans/" + planId + "/commits").with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id").value(c1.getId().toString()))
        .andExpect(jsonPath("$.data[0].title").value("first"))
        .andExpect(jsonPath("$.data[0].derived.carryStreak").value(3))
        .andExpect(jsonPath("$.data[0].derived.stuckFlag").value(true))
        .andExpect(jsonPath("$.data[1].derived.carryStreak").value(1))
        .andExpect(jsonPath("$.data[1].derived.stuckFlag").value(false));
  }

  @Test
  void listCommits_emptyPlan_returns200WithEmptyArray() throws Exception {
    UUID planId = UUID.randomUUID();
    when(commitService.findCommitsForPlan(any(), any())).thenReturn(List.of());

    mvc.perform(get("/api/v1/plans/" + planId + "/commits").with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void listCommits_unauthenticated_returns401() throws Exception {
    mvc.perform(get("/api/v1/plans/" + UUID.randomUUID() + "/commits"))
        .andExpect(status().isUnauthorized());
  }
}
