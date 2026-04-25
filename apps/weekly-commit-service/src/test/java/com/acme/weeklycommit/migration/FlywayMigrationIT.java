package com.acme.weeklycommit.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.weeklycommit.testsupport.PostgresTestContainer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Applies Flyway V1-V6 from scratch against a real Postgres 16.4 container and asserts the schema
 * is what we expect (tables, selected columns, key indexes). Also exercises a representative insert
 * on every owned table to catch constraint / type mismatches that pure DDL inspection misses.
 *
 * <p>Does NOT load the Spring context — this test focuses on migration correctness in isolation.
 */
@TestInstance(Lifecycle.PER_CLASS)
class FlywayMigrationIT {

  private DataSource ds;
  private Flyway flyway;

  @BeforeAll
  void setup() {
    PGSimpleDataSource pg = new PGSimpleDataSource();
    pg.setUrl(PostgresTestContainer.instance().getJdbcUrl());
    pg.setUser(PostgresTestContainer.instance().getUsername());
    pg.setPassword(PostgresTestContainer.instance().getPassword());
    this.ds = pg;

    this.flyway =
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load();

    flyway.clean();
    flyway.migrate();
  }

  @AfterAll
  void teardown() {
    if (flyway != null) {
      flyway.clean();
    }
  }

  @Test
  void allMigrationsApplied() {
    List<String> applied = appliedVersions();
    assertThat(applied).containsExactly("1", "2", "3", "4", "5", "6");
  }

  @Test
  void tablesCreated() {
    Set<String> tables =
        Set.copyOf(
            queryStrings(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"));
    assertThat(tables)
        .contains(
            "weekly_plan",
            "weekly_commit",
            "manager_review",
            "notification_dlt",
            "audit_log",
            "shedlock");
  }

  @Test
  void weeklyPlan_hasRequiredColumnsAndUniqueConstraint() {
    Set<String> cols = columnsOf("weekly_plan");
    assertThat(cols)
        .contains(
            "id",
            "employee_id",
            "week_start",
            "state",
            "locked_at",
            "reconciled_at",
            "manager_reviewed_at",
            "reflection_note",
            "version",
            "created_by",
            "created_date",
            "last_modified_by",
            "last_modified_date");

    assertThat(
            queryStrings(
                """
        SELECT conname FROM pg_constraint
         WHERE conrelid = 'weekly_plan'::regclass
           AND contype = 'u'
        """))
        .contains("uq_weekly_plan_employee_week");
  }

  @Test
  void weeklyCommit_toprockIndex_isComposite() {
    assertThat(
            queryStrings(
                """
        SELECT indexdef FROM pg_indexes
         WHERE tablename = 'weekly_commit'
           AND indexname = 'idx_weekly_commit_toprock'
        """))
        .hasSize(1)
        .anyMatch(def -> def.contains("(plan_id, chess_tier, display_order)"));
  }

  @Test
  void weeklyCommit_carryStreakIndex_exists() {
    assertThat(
            queryStrings(
                """
        SELECT indexname FROM pg_indexes
         WHERE tablename = 'weekly_commit'
        """))
        .contains("idx_weekly_commit_carry");
  }

  @Test
  void shedlockTable_hasExpectedShape() {
    Set<String> cols = columnsOf("shedlock");
    assertThat(cols).containsExactlyInAnyOrder("name", "lock_until", "locked_at", "locked_by");
  }

  @Test
  void happyPath_insertAcrossAllTables() throws SQLException {
    UUID planId = UUID.randomUUID();
    UUID commitId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    UUID dltId = UUID.randomUUID();
    UUID auditId = UUID.randomUUID();

    try (Connection c = ds.getConnection()) {
      c.setAutoCommit(true);

      // weekly_plan
      runUpdate(
          c,
          """
          INSERT INTO weekly_plan
            (id, employee_id, week_start, state, version,
             created_by, created_date, last_modified_by, last_modified_date)
          VALUES (?, ?, ?, 'DRAFT', 0, 'sys', now(), 'sys', now())
          """,
          planId,
          UUID.randomUUID(),
          LocalDate.parse("2026-04-27"));

      // weekly_commit
      runUpdate(
          c,
          """
          INSERT INTO weekly_commit
            (id, plan_id, title, supporting_outcome_id, chess_tier,
             display_order, actual_status,
             created_by, created_date, last_modified_by, last_modified_date)
          VALUES (?, ?, 'ship the thing', ?, 'ROCK', 0, 'PENDING',
                  'sys', now(), 'sys', now())
          """,
          commitId,
          planId,
          UUID.randomUUID());

      // manager_review
      runUpdate(
          c,
          """
          INSERT INTO manager_review
            (id, plan_id, manager_id, acknowledged_at,
             created_by, created_date, last_modified_by, last_modified_date)
          VALUES (?, ?, ?, now(), 'sys', now(), 'sys', now())
          """,
          reviewId,
          planId,
          UUID.randomUUID());

      // notification_dlt
      runUpdate(
          c,
          """
          INSERT INTO notification_dlt
            (id, event_type, payload, last_error, attempts)
          VALUES (?, 'WEEKLY_PLAN_LOCKED', '{"foo":"bar"}'::jsonb, 'boom', 3)
          """,
          dltId);

      // audit_log
      runUpdate(
          c,
          """
          INSERT INTO audit_log
            (id, entity_type, entity_id, event_type, from_state, to_state)
          VALUES (?, 'WEEKLY_PLAN', ?, 'STATE_TRANSITION', 'DRAFT', 'LOCKED')
          """,
          auditId,
          planId);
    }

    assertThat(countRows("SELECT COUNT(*) FROM weekly_plan")).isEqualTo(1);
    assertThat(countRows("SELECT COUNT(*) FROM weekly_commit")).isEqualTo(1);
    assertThat(countRows("SELECT COUNT(*) FROM manager_review")).isEqualTo(1);
    assertThat(countRows("SELECT COUNT(*) FROM notification_dlt")).isEqualTo(1);
    assertThat(countRows("SELECT COUNT(*) FROM audit_log")).isEqualTo(1);
  }

  @Test
  void cascadeDelete_planDeletesCommits() throws SQLException {
    UUID planId = UUID.randomUUID();
    UUID commitId = UUID.randomUUID();

    try (Connection c = ds.getConnection()) {
      c.setAutoCommit(true);
      runUpdate(
          c,
          """
          INSERT INTO weekly_plan
            (id, employee_id, week_start, state, version,
             created_by, created_date, last_modified_by, last_modified_date)
          VALUES (?, ?, ?, 'DRAFT', 0, 'sys', now(), 'sys', now())
          """,
          planId,
          UUID.randomUUID(),
          LocalDate.parse("2026-05-04"));
      runUpdate(
          c,
          """
          INSERT INTO weekly_commit
            (id, plan_id, title, supporting_outcome_id, chess_tier,
             display_order, actual_status,
             created_by, created_date, last_modified_by, last_modified_date)
          VALUES (?, ?, 't', ?, 'PEBBLE', 0, 'PENDING',
                  'sys', now(), 'sys', now())
          """,
          commitId,
          planId,
          UUID.randomUUID());

      runUpdate(c, "DELETE FROM weekly_plan WHERE id = ?", planId);
    }

    long remaining;
    try (Connection c = ds.getConnection();
        PreparedStatement ps =
            c.prepareStatement("SELECT COUNT(*) FROM weekly_commit WHERE id = ?")) {
      ps.setObject(1, commitId);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        remaining = rs.getLong(1);
      }
    }
    assertThat(remaining).isZero();
  }

  // --- helpers ---

  private List<String> appliedVersions() {
    return queryStrings(
        "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank");
  }

  private Set<String> columnsOf(String table) {
    return Set.copyOf(
        queryStringsWithParam(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = ?",
            table));
  }

  private List<String> queryStrings(String sql) {
    List<String> out = new ArrayList<>();
    try (Connection c = ds.getConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        out.add(rs.getString(1));
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
    return out;
  }

  private List<String> queryStringsWithParam(String sql, Object param) {
    List<String> out = new ArrayList<>();
    try (Connection c = ds.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setObject(1, param);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(rs.getString(1));
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
    return out;
  }

  private long countRows(String countSql) {
    try (Connection c = ds.getConnection();
        PreparedStatement ps = c.prepareStatement(countSql);
        ResultSet rs = ps.executeQuery()) {
      rs.next();
      return rs.getLong(1);
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private void runUpdate(Connection c, String sql, Object... params) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);
      }
      ps.execute();
    }
  }
}
