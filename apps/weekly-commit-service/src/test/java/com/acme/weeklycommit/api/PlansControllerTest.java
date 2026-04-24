package com.acme.weeklycommit.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.weeklycommit.api.dto.WeeklyPlanMapper;
import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.service.WeeklyPlanService;
import com.acme.weeklycommit.testsupport.WebMvcTestConfig;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PlansController.class)
@Import(WebMvcTestConfig.class)
class PlansControllerTest {

  private static final UUID EMPLOYEE_ID =
      UUID.fromString("00000000-0000-0000-0000-0000000000a1");

  @Autowired private MockMvc mvc;

  @MockBean private WeeklyPlanService planService;
  @MockBean private WeeklyPlanMapper mapper;

  @Test
  void getCurrentForMe_whenPlanExists_returns200WithEnvelope() throws Exception {
    WeeklyPlan plan = new WeeklyPlan(UUID.randomUUID(), EMPLOYEE_ID, LocalDate.parse("2026-04-27"));
    plan.setState(PlanState.DRAFT);
    when(planService.findCurrentWeekPlan(any())).thenReturn(Optional.of(plan));
    when(mapper.toResponse(plan))
        .thenReturn(
            new com.acme.weeklycommit.api.dto.WeeklyPlanResponse(
                plan.getId(),
                plan.getEmployeeId(),
                plan.getWeekStart(),
                plan.getState(),
                null,
                null,
                null,
                null,
                0L));

    mvc.perform(
            get("/api/v1/plans/me/current")
                .with(jwt().jwt(builder -> builder.subject(EMPLOYEE_ID.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(plan.getId().toString()))
        .andExpect(jsonPath("$.data.employeeId").value(EMPLOYEE_ID.toString()))
        .andExpect(jsonPath("$.data.state").value("DRAFT"))
        .andExpect(jsonPath("$.meta.now").isString());
  }

  @Test
  void getCurrentForMe_whenNoPlan_returns404() throws Exception {
    when(planService.findCurrentWeekPlan(any())).thenReturn(Optional.empty());

    mvc.perform(
            get("/api/v1/plans/me/current")
                .with(jwt().jwt(builder -> builder.subject(EMPLOYEE_ID.toString()))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void getCurrentForMe_unauthenticated_returns401() throws Exception {
    mvc.perform(get("/api/v1/plans/me/current")).andExpect(status().isUnauthorized());
  }

  @Test
  void createCurrentForMe_returns201WithEnvelope() throws Exception {
    WeeklyPlan created =
        new WeeklyPlan(UUID.randomUUID(), EMPLOYEE_ID, LocalDate.parse("2026-04-27"));
    when(planService.createCurrentWeekPlan(any())).thenReturn(created);
    when(mapper.toResponse(created))
        .thenReturn(
            new com.acme.weeklycommit.api.dto.WeeklyPlanResponse(
                created.getId(),
                created.getEmployeeId(),
                created.getWeekStart(),
                PlanState.DRAFT,
                null,
                null,
                null,
                null,
                0L));

    mvc.perform(
            post("/api/v1/plans")
                .with(jwt().jwt(builder -> builder.subject(EMPLOYEE_ID.toString()))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(created.getId().toString()))
        .andExpect(jsonPath("$.data.state").value("DRAFT"))
        .andExpect(jsonPath("$.meta.now").isString());
  }

  @Test
  void createCurrentForMe_unauthenticated_returns401() throws Exception {
    mvc.perform(post("/api/v1/plans")).andExpect(status().isUnauthorized());
  }
}
