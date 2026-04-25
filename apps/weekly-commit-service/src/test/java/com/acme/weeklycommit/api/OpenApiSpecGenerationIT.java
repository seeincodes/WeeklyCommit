package com.acme.weeklycommit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.weeklycommit.integration.notification.NotificationSender;
import com.acme.weeklycommit.testsupport.PostgresTestContainer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Generates {@code libs/contracts/openapi.yaml} from the running springdoc spec, OR asserts no
 * drift between code and the committed spec. Mode is selected at runtime:
 *
 * <ul>
 *   <li>{@code -Dgen-openapi=true} — overwrite {@code libs/contracts/openapi.yaml} with the
 *       freshly-generated spec. Used by the {@code gen-openapi} Maven profile and by developers who
 *       just changed an endpoint.
 *   <li>(default) — read the committed file and assert it matches. Drift fails the build, just like
 *       Spotless drift fails CI. Mirrors the warn-locally / fail-in-CI pattern.
 * </ul>
 *
 * <p>Suffix is {@code IT} (not {@code Test}) so it runs under Failsafe in {@code mvn verify}, the
 * stage where Spring context loads matter and infra checks live.
 *
 * <p>Boots the full Spring context against the shared Testcontainers Postgres — same pattern as
 * other ITs. The spec generator only needs MockMvc + springdoc, but excluding JPA from a
 * {@code @SpringBootTest} is brittle (cascading bean failures), so we let the standard
 * {@code @SpringBootTest} stack come up and rely on the singleton container reuse to keep the cost
 * low after first run.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class OpenApiSpecGenerationIT {

  // libs/contracts/openapi.yaml relative to the service module's working directory.
  private static final Path SPEC_PATH = Path.of("../../libs/contracts/openapi.yaml");

  @Autowired private MockMvc mvc;

  // LoggingNotificationSender's @ConditionalOnMissingBean conflicts with itself (it both
  // implements NotificationSender and asks "is there a NotificationSender bean already?"),
  // so the dev-profile auto-registration fails to fire. Mock the bean here to make context
  // load deterministic. The mock is never invoked — spec gen reads metadata, not behavior.
  @MockBean private NotificationSender notificationSender;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    PostgresTestContainer.register(registry);
    registry.add("AUTH0_ISSUER_URI", () -> "https://test.invalid/");
    registry.add("AUTH0_AUDIENCE", () -> "test-audience");
  }

  @Test
  void specMatchesCommittedFile() throws Exception {
    String generated = fetchSpec();

    if (Boolean.parseBoolean(System.getProperty("gen-openapi", "false"))) {
      Files.writeString(SPEC_PATH, generated);
      System.out.println("[gen-openapi] wrote " + SPEC_PATH.toAbsolutePath());
      return;
    }

    String committed = readCommittedSpec();
    assertThat(generated)
        .as(
            "OpenAPI spec drift: %s does not match the running springdoc output. "
                + "Regenerate with: ./mvnw -pl apps/weekly-commit-service verify -Pgen-openapi",
            SPEC_PATH)
        .isEqualTo(committed);
  }

  private String fetchSpec() throws Exception {
    MvcResult result = mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk()).andReturn();
    return result.getResponse().getContentAsString();
  }

  private String readCommittedSpec() throws IOException {
    if (!Files.exists(SPEC_PATH)) {
      throw new IllegalStateException(
          "Expected committed spec at "
              + SPEC_PATH.toAbsolutePath()
              + " — generate it with `./mvnw -pl apps/weekly-commit-service verify -Pgen-openapi`");
    }
    return Files.readString(SPEC_PATH);
  }
}
