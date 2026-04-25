package com.acme.weeklycommit.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Postgres 16.4 container for integration tests. Singleton: one JVM-wide instance is started
 * on first use and reused across test classes to keep CI fast (ERROR_FIX_LOG.md note on
 * Testcontainers startup cost).
 *
 * <p>Usage: {@code @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
 * PostgresTestContainer.register(r); } }
 */
public final class PostgresTestContainer {

  private static final PostgreSQLContainer<?> INSTANCE =
      new PostgreSQLContainer<>("postgres:16.4-alpine")
          .withDatabaseName("weeklycommit_test")
          .withUsername("wc_test")
          .withPassword("wc_test")
          .withReuse(true);

  static {
    INSTANCE.start();
  }

  private PostgresTestContainer() {}

  public static void register(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", INSTANCE::getJdbcUrl);
    registry.add("spring.datasource.username", INSTANCE::getUsername);
    registry.add("spring.datasource.password", INSTANCE::getPassword);
    registry.add("spring.flyway.clean-disabled", () -> "false");
  }

  public static PostgreSQLContainer<?> instance() {
    return INSTANCE;
  }
}
