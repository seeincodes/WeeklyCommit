package com.acme.weeklycommit.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.AuditLog;
import com.acme.weeklycommit.domain.enums.AuditEntityType;
import com.acme.weeklycommit.domain.enums.AuditEventType;
import com.acme.weeklycommit.service.AuditService;
import com.acme.weeklycommit.testsupport.WebMvcTestConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuditController.class)
@Import(WebMvcTestConfig.class)
@ActiveProfiles("test")
class AuditControllerTest {

  private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  @Autowired private MockMvc mvc;

  @MockBean private AuditService auditService;

  private static JwtRequestPostProcessor validJwt() {
    return jwt().jwt(b -> b.subject(EMPLOYEE_ID.toString()).claim("org_id", ORG_ID.toString()));
  }

  @Test
  void getAuditForPlan_200WithEnvelope() throws Exception {
    UUID planId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    AuditLog row =
        new AuditLog(
            UUID.fromString("00000000-0000-0000-0000-000000000aa1"),
            AuditEntityType.WEEKLY_PLAN,
            planId,
            AuditEventType.STATE_TRANSITION,
            actorId);
    row.setFromState("DRAFT");
    row.setToState("LOCKED");
    when(auditService.findForPlan(eq(planId), any(AuthenticatedPrincipal.class)))
        .thenReturn(List.of(row));

    mvc.perform(get("/api/v1/audit/plans/{id}", planId).with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(row.getId().toString()))
        .andExpect(jsonPath("$.data[0].entityType").value("WEEKLY_PLAN"))
        .andExpect(jsonPath("$.data[0].eventType").value("STATE_TRANSITION"))
        .andExpect(jsonPath("$.data[0].fromState").value("DRAFT"))
        .andExpect(jsonPath("$.data[0].toState").value("LOCKED"))
        .andExpect(jsonPath("$.data[0].actorId").value(actorId.toString()))
        .andExpect(jsonPath("$.data[0].occurredAt").isString())
        .andExpect(jsonPath("$.meta.now").isString());
  }

  @Test
  void getAuditForPlan_emptyArray_passesThrough() throws Exception {
    UUID planId = UUID.randomUUID();
    when(auditService.findForPlan(eq(planId), any(AuthenticatedPrincipal.class)))
        .thenReturn(List.of());

    mvc.perform(get("/api/v1/audit/plans/{id}", planId).with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void getAuditForPlan_403_whenServiceDeniesAccess() throws Exception {
    UUID planId = UUID.randomUUID();
    when(auditService.findForPlan(eq(planId), any(AuthenticatedPrincipal.class)))
        .thenThrow(new AccessDeniedException("denied"));

    mvc.perform(get("/api/v1/audit/plans/{id}", planId).with(validJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void getAuditForPlan_404_whenPlanNotFound() throws Exception {
    UUID planId = UUID.randomUUID();
    when(auditService.findForPlan(eq(planId), any(AuthenticatedPrincipal.class)))
        .thenThrow(new ResourceNotFoundException("WeeklyPlan", planId));

    mvc.perform(get("/api/v1/audit/plans/{id}", planId).with(validJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void getAuditForPlan_401_whenUnauthenticated() throws Exception {
    UUID planId = UUID.randomUUID();
    mvc.perform(get("/api/v1/audit/plans/{id}", planId)).andExpect(status().isUnauthorized());
    verify(auditService, never()).findForPlan(any(), any());
  }

  @Test
  void getAuditForPlan_400_whenPlanIdNotUuid() throws Exception {
    mvc.perform(get("/api/v1/audit/plans/{id}", "not-a-uuid").with(validJwt()))
        .andExpect(status().isBadRequest());
    verify(auditService, never()).findForPlan(any(), any());
  }
}
