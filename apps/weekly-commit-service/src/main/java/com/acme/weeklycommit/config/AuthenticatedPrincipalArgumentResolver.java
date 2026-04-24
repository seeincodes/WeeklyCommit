package com.acme.weeklycommit.config;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets controllers declare {@link AuthenticatedPrincipal} as a parameter and receive the
 * validated principal for the current request. Centralizes JWT-claim extraction in one place
 * so controllers never see raw {@code Jwt} or {@code Authentication}.
 *
 * <p>Fails loudly if called outside of an authenticated request — Spring Security should have
 * already rejected unauthenticated traffic at the filter chain; reaching this resolver without
 * a {@link JwtAuthenticationToken} in the context is a wiring bug, not an expected state.
 *
 * <p>Registered in {@link WebMvcConfig}.
 */
@Component
public class AuthenticatedPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return AuthenticatedPrincipal.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new IllegalStateException(
          "AuthenticatedPrincipal requested but request is not authenticated");
    }
    return AuthenticatedPrincipal.of(auth);
  }
}
