package com.acme.weeklycommit.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Executes {@code scripts/lint-now-thresholds.sh} as part of the unit test suite. The script
 * enforces the week-math rule (MEMO decision: never {@code NOW()} in SQL for threshold comparisons;
 * always pass an application-computed {@link java.time.Instant}). Wiring it into {@code mvnw test}
 * means CI catches drift even if a developer skips running the lint by hand.
 *
 * <p>The script is the source of truth for allowlist logic; this test only checks that it exits
 * cleanly on the current tree. If a forbidden {@code NOW()} comparison is introduced, the test
 * fails with the script's stderr captured in the assertion message.
 */
class NowThresholdLintTest {

  @Test
  void lintScript_passesOnCurrentTree() throws IOException, InterruptedException {
    // Test runs with cwd = apps/weekly-commit-service; the script lives at <repo-root>/scripts/.
    Path script = Path.of("../../scripts/lint-now-thresholds.sh").toAbsolutePath().normalize();
    assertThat(Files.exists(script)).as("lint script must exist at %s", script).isTrue();

    ProcessBuilder pb = new ProcessBuilder("bash", script.toString());
    pb.redirectErrorStream(true);
    Process proc = pb.start();

    String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    boolean finished = proc.waitFor(60, TimeUnit.SECONDS);
    assertThat(finished).as("lint script must terminate within 60s").isTrue();

    int exit = proc.exitValue();
    assertThat(exit)
        .as(
            "lint-now-thresholds.sh failed (exit=%d). Output:%n%s%n"
                + "See docs/MEMO.md week-math decision; replace NOW() with application Instant.",
            exit, output)
        .isEqualTo(0);
  }
}
