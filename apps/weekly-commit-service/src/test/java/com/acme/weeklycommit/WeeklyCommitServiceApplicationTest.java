package com.acme.weeklycommit;

import org.junit.jupiter.api.Test;

/**
 * Smoke test that asserts the main class exists. Full context-load tests that require a DB live
 * alongside integration tests (group 4).
 */
class WeeklyCommitServiceApplicationTest {

  @Test
  void mainClassPresent() {
    // Existence check — the compile step is the real assertion.
    org.junit.jupiter.api.Assertions.assertNotNull(
        WeeklyCommitServiceApplication.class.getName());
  }
}
