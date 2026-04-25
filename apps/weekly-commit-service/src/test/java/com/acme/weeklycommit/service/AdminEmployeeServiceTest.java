package com.acme.weeklycommit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.Employee;
import com.acme.weeklycommit.repo.EmployeeRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AdminEmployeeServiceTest {

  @Mock private EmployeeRepository employees;

  private AdminEmployeeService service() {
    return new AdminEmployeeService(employees);
  }

  @Test
  void listUnassigned_throwsAccessDenied_whenCallerLacksAdmin() {
    AuthenticatedPrincipal caller = principal(UUID.randomUUID(), UUID.randomUUID(), Set.of());

    assertThatThrownBy(() -> service().listUnassignedEmployees(caller))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("ADMIN");

    verify(employees, never()).findUnassignedInOrg(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void listUnassigned_throwsAccessDenied_forManagerRoleAlone() {
    AuthenticatedPrincipal caller =
        principal(UUID.randomUUID(), UUID.randomUUID(), Set.of("MANAGER"));

    assertThatThrownBy(() -> service().listUnassignedEmployees(caller))
        .isInstanceOf(AccessDeniedException.class);

    verify(employees, never()).findUnassignedInOrg(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void listUnassigned_returnsRowsScopedToCallerOrg_whenAdmin() {
    UUID orgId = UUID.randomUUID();
    UUID otherOrg = UUID.randomUUID();
    AuthenticatedPrincipal caller = principal(UUID.randomUUID(), orgId, Set.of("ADMIN"));

    Employee a = new Employee(UUID.randomUUID(), orgId);
    a.setDisplayName("Unassigned A");
    Employee b = new Employee(UUID.randomUUID(), orgId);
    b.setDisplayName("Unassigned B");
    when(employees.findUnassignedInOrg(orgId)).thenReturn(List.of(a, b));

    List<Employee> result = service().listUnassignedEmployees(caller);

    assertThat(result).containsExactly(a, b);
    verify(employees).findUnassignedInOrg(orgId);
    verify(employees, never()).findUnassignedInOrg(otherOrg);
  }

  @Test
  void listUnassigned_returnsEmptyList_whenAdminOrgHasNoUnassigned() {
    UUID orgId = UUID.randomUUID();
    AuthenticatedPrincipal caller = principal(UUID.randomUUID(), orgId, Set.of("ADMIN"));
    when(employees.findUnassignedInOrg(orgId)).thenReturn(List.of());

    assertThat(service().listUnassignedEmployees(caller)).isEmpty();
  }

  private static AuthenticatedPrincipal principal(UUID employeeId, UUID orgId, Set<String> roles) {
    Jwt jwt =
        new Jwt(
            "t",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", employeeId.toString(),
                "org_id", orgId.toString(),
                "timezone", "UTC",
                "roles", List.copyOf(roles)));
    return new AuthenticatedPrincipal(
        employeeId, orgId, Optional.empty(), roles, ZoneId.of("UTC"), jwt);
  }
}
