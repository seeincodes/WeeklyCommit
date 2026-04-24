package com.acme.weeklycommit.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the {@link AuthenticatedPrincipalArgumentResolver} so controllers can declare an
 * {@link AuthenticatedPrincipal} parameter without further ceremony.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  private final AuthenticatedPrincipalArgumentResolver principalResolver;

  public WebMvcConfig(AuthenticatedPrincipalArgumentResolver principalResolver) {
    this.principalResolver = principalResolver;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(principalResolver);
  }
}
