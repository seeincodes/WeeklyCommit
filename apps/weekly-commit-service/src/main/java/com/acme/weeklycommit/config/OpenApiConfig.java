package com.acme.weeklycommit.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

/**
 * Code-first OpenAPI metadata. The actual paths and schemas are derived from controllers and DTOs
 * by springdoc; this class only provides the document-level fields that aren't inferable.
 *
 * <p>The {@code BearerAuth} scheme tells consumers "every endpoint expects a JWT in the {@code
 * Authorization} header" — applied as a global default via {@link SecurityRequirement}. Endpoints
 * that don't require auth (Actuator health/info, the spec itself) are not in scope of this
 * application's OpenAPI document.
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Weekly Commit API",
            version = "0.1.0",
            description =
                "REST contract between weekly-commit-ui (Module Federation remote) and "
                    + "weekly-commit-service. Generated from controller annotations; "
                    + "regenerate via `./mvnw verify -Pgen-openapi` whenever endpoints change."),
    servers = {@Server(url = "/api/v1", description = "Default server (relative)")},
    security = {@SecurityRequirement(name = "BearerAuth")})
@SecurityScheme(
    name = "BearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER,
    description = "Auth0-issued JWT. Required on every /api/v1/** endpoint.")
public class OpenApiConfig {

  static {
    // AuthenticatedPrincipal is resolved by AuthenticatedPrincipalArgumentResolver from the
    // SecurityContext, not from request data. Strip it from the spec so it doesn't appear as a
    // bogus parameter on every endpoint.
    SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthenticatedPrincipal.class);
  }
}
