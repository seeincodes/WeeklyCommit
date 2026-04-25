package com.acme.weeklycommit.service;

import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.Employee;
import com.acme.weeklycommit.repo.EmployeeRepository;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only operations against the {@link Employee} projection. Today this is just the unassigned
 * report, which surfaces employees whose Auth0 record has no manager attached.
 *
 * <p>Authz is fail-closed: any caller without the {@code ADMIN} role is rejected <b>before</b> the
 * DB lookup, so a peer's unassigned roster is never read off-disk and into the JVM. Org scoping is
 * enforced inside the query — an ADMIN in org A cannot ask for org B's roster, because the only
 * org_id the service trusts is the one on the caller's validated JWT.
 */
@Service
public class AdminEmployeeService {

  private final EmployeeRepository employees;

  public AdminEmployeeService(EmployeeRepository employees) {
    this.employees = employees;
  }

  @Transactional(readOnly = true)
  public List<Employee> listUnassignedEmployees(AuthenticatedPrincipal caller) {
    if (!caller.hasRole("ADMIN")) {
      throw new AccessDeniedException(
          "caller " + caller.employeeId() + " requires ADMIN role to list unassigned employees");
    }
    return employees.findUnassignedInOrg(caller.organizationId());
  }
}
