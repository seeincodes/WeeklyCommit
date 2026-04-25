package com.acme.weeklycommit.repo;

import com.acme.weeklycommit.domain.entity.Employee;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

  /**
   * Direct reports of the given manager. Uses the partial index {@code idx_employee_manager}.
   * Filters to active rows so deactivated employees don't appear in rollups.
   */
  @Query(
      """
          SELECT e FROM Employee e
           WHERE e.managerId = :managerId
             AND e.active = true
          """)
  List<Employee> findDirectReports(@Param("managerId") UUID managerId);

  /**
   * Employees in an org with no assigned manager. Powers {@code GET /admin/unassigned-employees}.
   * Uses {@code idx_employee_unassigned} (partial index on manager_id IS NULL).
   */
  @Query(
      """
          SELECT e FROM Employee e
           WHERE e.orgId = :orgId
             AND e.managerId IS NULL
             AND e.active = true
          """)
  List<Employee> findUnassignedInOrg(@Param("orgId") UUID orgId);
}
