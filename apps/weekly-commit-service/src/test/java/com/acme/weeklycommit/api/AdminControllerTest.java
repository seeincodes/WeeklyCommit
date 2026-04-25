package com.acme.weeklycommit.api;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.Employee;
import com.acme.weeklycommit.service.AdminEmployeeService;
import com.acme.weeklycommit.testsupport.WebMvcTestConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminController.class)
@Import(WebMvcTestConfig.class)
@ActiveProfiles("test")
class AdminControllerTest {

  private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  @Autowired private MockMvc mvc;

  @MockBean private AdminEmployeeService adminEmployeeService;

  private static JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(b -> b.subject(EMPLOYEE_ID.toString()).claim("org_id", ORG_ID.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private static JwtRequestPostProcessor managerJwt() {
    return jwt()
        .jwt(b -> b.subject(EMPLOYEE_ID.toString()).claim("org_id", ORG_ID.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
  }

  private static JwtRequestPostProcessor noRoleJwt() {
    return jwt().jwt(b -> b.subject(EMPLOYEE_ID.toString()).claim("org_id", ORG_ID.toString()));
  }

  @Test
  void listUnassigned_200WithEnvelope_whenAdmin() throws Exception {
    Employee a = new Employee(UUID.fromString("00000000-0000-0000-0000-000000000aa1"), ORG_ID);
    a.setDisplayName("Ada Lovelace");
    Employee b = new Employee(UUID.fromString("00000000-0000-0000-0000-000000000bb2"), ORG_ID);
    b.setDisplayName("Bob");
    when(adminEmployeeService.listUnassignedEmployees(
            org.mockito.ArgumentMatchers.any(AuthenticatedPrincipal.class)))
        .thenReturn(List.of(a, b));

    mvc.perform(get("/api/v1/admin/unassigned-employees").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id").value(a.getId().toString()))
        .andExpect(jsonPath("$.data[0].displayName").value("Ada Lovelace"))
        .andExpect(jsonPath("$.data[1].id").value(b.getId().toString()))
        .andExpect(jsonPath("$.data[1].displayName").value("Bob"))
        .andExpect(jsonPath("$.meta.now").isString());
  }

  @Test
  void listUnassigned_emptyArray_whenNoUnassigned() throws Exception {
    when(adminEmployeeService.listUnassignedEmployees(
            org.mockito.ArgumentMatchers.any(AuthenticatedPrincipal.class)))
        .thenReturn(List.of());

    mvc.perform(get("/api/v1/admin/unassigned-employees").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void listUnassigned_403_whenManagerOnly() throws Exception {
    mvc.perform(get("/api/v1/admin/unassigned-employees").with(managerJwt()))
        .andExpect(status().isForbidden());
    verify(adminEmployeeService, never())
        .listUnassignedEmployees(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void listUnassigned_403_whenNoRoles() throws Exception {
    mvc.perform(get("/api/v1/admin/unassigned-employees").with(noRoleJwt()))
        .andExpect(status().isForbidden());
    verify(adminEmployeeService, never())
        .listUnassignedEmployees(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void listUnassigned_401_whenUnauthenticated() throws Exception {
    mvc.perform(get("/api/v1/admin/unassigned-employees")).andExpect(status().isUnauthorized());
    verify(adminEmployeeService, never())
        .listUnassignedEmployees(org.mockito.ArgumentMatchers.any());
  }
}
