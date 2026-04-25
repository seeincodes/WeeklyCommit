package com.acme.weeklycommit.integration.rcdo;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Read-only client for the upstream RCDO service. Contract: docs/adr/0001-rcdo-contract.md.
 *
 * <p><b>Resilience semantics:</b> Both methods are wrapped by Resilience4j retry + circuit breaker
 * (instance name {@code "rcdo"}, configured in {@code application.yml}). 404 is the expected
 * response for deleted/deactivated outcomes and is mapped to {@link Optional#empty()} before retry
 * can see it — retrying a 404 is wasted work and would burn through the budget. 5xx and IO errors
 * propagate so the AOP layer can apply backoff and circuit-breaking.
 *
 * <p><b>Auth:</b> the {@link WebClient} bean is configured with the service-token bearer header by
 * {@link RcdoWebClientConfig}, so this class does not handle credentials directly.
 */
public class RcdoClient {

  private static final ParameterizedTypeReference<RcdoEnvelope<SupportingOutcomeView>>
      SINGLE_ENVELOPE = new ParameterizedTypeReference<>() {};

  private static final ParameterizedTypeReference<RcdoEnvelope<List<SupportingOutcomeView>>>
      LIST_ENVELOPE = new ParameterizedTypeReference<>() {};

  private final WebClient webClient;
  private final Duration timeout;

  public RcdoClient(WebClient webClient, Duration timeout) {
    this.webClient = webClient;
    this.timeout = timeout;
  }

  /**
   * Hydrate a single Supporting Outcome by id. Returns {@link Optional#empty()} on 404 — the
   * outcome was deleted or deactivated upstream, which is normal for old commits and not an error.
   */
  @Retry(name = "rcdo")
  @CircuitBreaker(name = "rcdo")
  public Optional<SupportingOutcomeView> findSupportingOutcome(UUID id) {
    try {
      RcdoEnvelope<SupportingOutcomeView> envelope =
          webClient
              .get()
              .uri(
                  uri ->
                      uri.path("/rcdo/supporting-outcomes/{id}")
                          .queryParam("hydrate", "full")
                          .build(id))
              .retrieve()
              .bodyToMono(SINGLE_ENVELOPE)
              .block(timeout);
      return Optional.ofNullable(envelope).map(RcdoEnvelope::data);
    } catch (WebClientResponseException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        return Optional.empty();
      }
      throw e;
    }
  }

  /** Active Supporting Outcomes scoped to an org — powers the picker. */
  @Retry(name = "rcdo")
  @CircuitBreaker(name = "rcdo")
  public List<SupportingOutcomeView> findActiveSupportingOutcomes(UUID orgId) {
    RcdoEnvelope<List<SupportingOutcomeView>> envelope =
        webClient
            .get()
            .uri(
                uri ->
                    uri.path("/rcdo/supporting-outcomes")
                        .queryParam("orgId", orgId.toString())
                        .queryParam("active", "true")
                        .build())
            .retrieve()
            .bodyToMono(LIST_ENVELOPE)
            .block(timeout);
    if (envelope == null || envelope.data() == null) {
      return List.of();
    }
    return envelope.data();
  }
}
