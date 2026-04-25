package com.acme.weeklycommit.testsupport;

import static org.mockito.Mockito.mock;

import com.acme.weeklycommit.config.AuthenticatedPrincipalArgumentResolver;
import com.acme.weeklycommit.config.SecurityConfig;
import com.acme.weeklycommit.config.WebMvcConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Shared bootstrap for {@code @WebMvcTest} slice tests. Imports the argument resolver + MVC config
 * so {@link com.acme.weeklycommit.config.AuthenticatedPrincipal} resolves in controllers, imports
 * {@link com.acme.weeklycommit.config.SecurityConfig} so tests exercise the same security chain as
 * prod, and shadows the OAuth2-autoconfigured {@link JwtDecoder} with a mock so context load does
 * not try to fetch JWKs from {@code AUTH0_ISSUER_URI}.
 *
 * <p>Tests opt in with:
 *
 * <pre>
 *   &#64;WebMvcTest(controllers = FooController.class)
 *   &#64;Import(WebMvcTestConfig.class)
 *   &#64;ActiveProfiles("test")     // loads application-test.yml with placeholder defaults
 *   class FooControllerTest { ... }
 * </pre>
 *
 * <p>{@code @ActiveProfiles} must be on the test class itself — it is test-context metadata and
 * does not propagate through {@code @Import}.
 */
@TestConfiguration
@Import({AuthenticatedPrincipalArgumentResolver.class, WebMvcConfig.class, SecurityConfig.class})
public class WebMvcTestConfig {

  /**
   * Mock decoder — never invoked in tests because {@code .with(jwt())} injects the Authentication
   * directly. Exists so context load can find a {@link JwtDecoder} bean.
   */
  @Bean
  JwtDecoder jwtDecoder() {
    return mock(JwtDecoder.class);
  }
}
