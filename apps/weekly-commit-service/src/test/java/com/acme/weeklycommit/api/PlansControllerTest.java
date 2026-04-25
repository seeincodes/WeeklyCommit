package com.acme.weeklycommit.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PlansController.class)
@Import(WebMvcTestConfig.class)
@ActiveProfiles("test")
class PlansControllerTest {

  private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  @Autowired private MockMvc mvc;

  @MockBean private WeeklyPlanService planService;
  @MockBean private WeeklyPlanMapper mapper;

  /**
   * Valid JWT for the canonical test subject. Sets both required claims: {@code sub} (employeeId)
   * and {@code org_id} — {@link com.acme.weeklycommit.config.AuthenticatedPrincipal} throws {@code
   * IllegalStateException} if either is missing, and that would surface as a 500 in @WebMvcTest.
   */
  private static JwtRequestPostProcessor validJwt() {
    return jwt()
        .jwt(builder -> builder.subject(EMPLOYEE_ID.toString()).claim("org_id", ORG_ID.toString()));
  }

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

    mvc.perform(get("/api/v1/plans/me/current").with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(plan.getId().toString()))
        .andExpect(jsonPath("$.data.employeeId").value(EMPLOYEE_ID.toString()))
        .andExpect(jsonPath("$.data.state").value("DRAFT"))
        .andExpect(jsonPath("$.meta.now").isString());
  }

  @Test
  void getCurrentForMe_whenNoPlan_returns404() throws Exception {
    when(planService.findCurrentWeekPlan(any())).thenReturn(Optional.empty());

    mvc.perform(get("/api/v1/plans/me/current").with(validJwt()))
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

    mvc.perform(post("/api/v1/plans").with(validJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(created.getId().toString()))
        .andExpect(jsonPath("$.data.state").value("DRAFT"))
        .andExpect(jsonPath("$.meta.now").isString());
  }

  @Test
  void createCurrentForMe_unauthenticated_returns401() throws Exception {
    mvc.perform(post("/api/v1/plans")).andExpect(status().isUnauthorized());
  }

  // --- GET /api/v1/plans?employeeId=...&weekStart=... ---

  @Test
  void getPlanByEmployeeAndWeek_200WhenFound() throws Exception {
    UUID targetEmployeeId = UUID.randomUUID();
    LocalDate weekStart = LocalDate.parse("2026-04-27");
    WeeklyPlan plan = new WeeklyPlan(UUID.randomUUID(), targetEmployeeId, weekStart);
    when(planService.findPlan(any(), any(), any())).thenReturn(Optional.of(plan));
    when(mapper.toResponse(plan))
        .thenReturn(
            new com.acme.weeklycommit.api.dto.WeeklyPlanResponse(
                plan.getId(),
                targetEmployeeId,
                weekStart,
                PlanState.DRAFT,
                null,
                null,
                null,
                null,
                0L));

    mvc.perform(
            get("/api/v1/plans")
                .param("employeeId", targetEmployeeId.toString())
                .param("weekStart", "2026-04-27")
                .with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(plan.getId().toString()))
        .andExpect(jsonPath("$.data.employeeId").value(targetEmployeeId.toString()));
  }

  @Test
  void getPlanByEmployeeAndWeek_404WhenMissing() throws Exception {
    when(planService.findPlan(any(), any(), any())).thenReturn(Optional.empty());

    mvc.perform(
            get("/api/v1/plans")
                .param("employeeId", UUID.randomUUID().toString())
                .param("weekStart", "2026-04-27")
                .with(validJwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void getPlanByEmployeeAndWeek_400OnMissingRequiredParam() throws Exception {
    mvc.perform(get("/api/v1/plans").with(validJwt())).andExpect(status().isBadRequest());
  }

  @Test
  void getPlanByEmployeeAndWeek_400OnMalformedUuid() throws Exception {
    mvc.perform(
            get("/api/v1/plans")
                .param("employeeId", "not-a-uuid")
                .param("weekStart", "2026-04-27")
                .with(validJwt()))
        .andExpect(status().isBadRequest());
  }

  // --- POST /api/v1/plans/{id}/transitions ---

  @Test
  void transition_200OnHappyPath() throws Exception {
    UUID planId = UUID.randomUUID();
    WeeklyPlan transitioned = new WeeklyPlan(planId, EMPLOYEE_ID, LocalDate.parse("2026-04-27"));
    transitioned.setState(PlanState.LOCKED);
    when(planService.transitionPlan(any(), any(), any())).thenReturn(transitioned);
    when(mapper.toResponse(transitioned))
        .thenReturn(
            new com.acme.weeklycommit.api.dto.WeeklyPlanResponse(
                planId,
                EMPLOYEE_ID,
                LocalDate.parse("2026-04-27"),
                PlanState.LOCKED,
                null,
                null,
                null,
                null,
                1L));

    mvc.perform(
            post("/api/v1/plans/" + planId + "/transitions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"to\":\"LOCKED\"}")
                .with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.state").value("LOCKED"));
  }

  @Test
  void transition_400WhenBodyMissingTo() throws Exception {
    UUID planId = UUID.randomUUID();

    mvc.perform(
            post("/api/v1/plans/" + planId + "/transitions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}")
                .with(validJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void transition_422WhenStateMachineRejects() throws Exception {
    UUID planId = UUID.randomUUID();
    when(planService.transitionPlan(any(), any(), any()))
        .thenThrow(
            new com.acme.weeklycommit.api.exception.InvalidStateTransitionException(
                "DRAFT", "RECONCILED", "not allowed from DRAFT"));

    mvc.perform(
            post("/api/v1/plans/" + planId + "/transitions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"to\":\"RECONCILED\"}")
                .with(validJwt()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"))
        .andExpect(jsonPath("$.meta.fromState").value("DRAFT"))
        .andExpect(jsonPath("$.meta.toState").value("RECONCILED"));
  }

  // --- PATCH /api/v1/plans/{id} ---

  @Test
  void updateReflection_200OnHappyPath() throws Exception {
    UUID planId = UUID.randomUUID();
    WeeklyPlan updated = new WeeklyPlan(planId, EMPLOYEE_ID, LocalDate.parse("2026-04-27"));
    updated.setState(PlanState.LOCKED);
    updated.setReflectionNote("shipped the picker");
    when(planService.updateReflectionNote(any(), any(), any())).thenReturn(updated);
    when(mapper.toResponse(updated))
        .thenReturn(
            new com.acme.weeklycommit.api.dto.WeeklyPlanResponse(
                planId,
                EMPLOYEE_ID,
                LocalDate.parse("2026-04-27"),
                PlanState.LOCKED,
                null,
                null,
                null,
                "shipped the picker",
                1L));

    mvc.perform(
            patch("/api/v1/plans/" + planId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"reflectionNote\":\"shipped the picker\"}")
                .with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reflectionNote").value("shipped the picker"));
  }

  @Test
  void updateReflection_400OnOversizedNote() throws Exception {
    UUID planId = UUID.randomUUID();
    // 501 chars — exceeds @Size(max=500)
    String oversized = "x".repeat(501);

    mvc.perform(
            patch("/api/v1/plans/" + planId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"reflectionNote\":\"" + oversized + "\"}")
                .with(validJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void updateReflection_unauthenticated_returns401() throws Exception {
    UUID planId = UUID.randomUUID();

    mvc.perform(
            patch("/api/v1/plans/" + planId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"reflectionNote\":\"x\"}"))
        .andExpect(status().isUnauthorized());
  }

  // --- GET /api/v1/plans/team?managerId=&weekStart= ---

  @Test
  void getTeam_200WithPagedEnvelope() throws Exception {
    UUID managerId = UUID.randomUUID();
    LocalDate week = LocalDate.parse("2026-04-27");
    WeeklyPlan p1 = new WeeklyPlan(UUID.randomUUID(), UUID.randomUUID(), week);
    WeeklyPlan p2 = new WeeklyPlan(UUID.randomUUID(), UUID.randomUUID(), week);
    org.springframework.data.domain.Page<WeeklyPlan> servicePage =
        new org.springframework.data.domain.PageImpl<>(
            java.util.List.of(p1, p2), org.springframework.data.domain.PageRequest.of(0, 20), 2);
    when(planService.findTeamPlans(any(), any(), any(), any())).thenReturn(servicePage);
    when(mapper.toResponse(any(WeeklyPlan.class)))
        .thenAnswer(
            inv -> {
              WeeklyPlan p = inv.getArgument(0);
              return new com.acme.weeklycommit.api.dto.WeeklyPlanResponse(
                  p.getId(),
                  p.getEmployeeId(),
                  p.getWeekStart(),
                  PlanState.DRAFT,
                  null,
                  null,
                  null,
                  null,
                  0L);
            });

    mvc.perform(
            get("/api/v1/plans/team")
                .param("managerId", managerId.toString())
                .param("weekStart", "2026-04-27")
                .with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id").value(p1.getId().toString()))
        .andExpect(jsonPath("$.meta.totalElements").value(2))
        .andExpect(jsonPath("$.meta.page").value(0))
        .andExpect(jsonPath("$.meta.size").value(20));
  }

  @Test
  void getTeam_400WhenSizeExceedsCap() throws Exception {
    mvc.perform(
            get("/api/v1/plans/team")
                .param("managerId", UUID.randomUUID().toString())
                .param("weekStart", "2026-04-27")
                .param("size", "101")
                .with(validJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void getTeam_400WhenMissingManagerId() throws Exception {
    mvc.perform(get("/api/v1/plans/team").param("weekStart", "2026-04-27").with(validJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getTeam_unauthenticated_returns401() throws Exception {
    mvc.perform(
            get("/api/v1/plans/team")
                .param("managerId", UUID.randomUUID().toString())
                .param("weekStart", "2026-04-27"))
        .andExpect(status().isUnauthorized());
  }
}
