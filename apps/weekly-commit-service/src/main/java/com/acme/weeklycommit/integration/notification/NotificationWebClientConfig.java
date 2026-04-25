package com.acme.weeklycommit.integration.notification;

import com.acme.weeklycommit.repo.NotificationDltRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Wires the production notification path: WebClient → NotificationClient →
 * ResilientNotificationSender. Active in any non-test, non-explicit-stub profile so dev pointed at
 * a real notification-svc URL gets real semantics. {@link LoggingNotificationSender} remains the
 * dev fallback when the notification surface is intentionally absent.
 */
@Configuration
class NotificationWebClientConfig {

  @Bean
  WebClient notificationWebClient(
      @Value("${weekly-commit.notification.base-url}") String baseUrl,
      @Value("${weekly-commit.notification.timeout-ms:3000}") long timeoutMs,
      @Value("${weekly-commit.notification.service-token:}") String serviceToken) {
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) Math.min(timeoutMs, 1000))
            .doOnConnected(
                conn ->
                    conn.addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));
    WebClient.Builder builder =
        WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient));
    if (serviceToken != null && !serviceToken.isBlank()) {
      builder.defaultHeader("Authorization", "Bearer " + serviceToken);
    }
    return builder.build();
  }

  @Bean
  NotificationClient notificationClient(
      WebClient notificationWebClient,
      @Value("${weekly-commit.notification.timeout-ms:3000}") long timeoutMs) {
    return new NotificationClient(notificationWebClient, Duration.ofMillis(timeoutMs));
  }

  /**
   * Production-grade sender with Retry + CircuitBreaker + DLT. Activates only in {@code prod}
   * profile so non-prod environments keep the {@link LoggingNotificationSender} fallback when no
   * real notification-svc is on the other end.
   */
  @Bean
  @Profile("prod")
  NotificationSender resilientNotificationSender(
      NotificationClient client,
      NotificationDltRepository dltRepo,
      ObjectMapper objectMapper,
      RetryRegistry retryRegistry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    Retry retry = retryRegistry.retry("notification");
    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("notification");
    return new ResilientNotificationSender(client, dltRepo, objectMapper, retry, circuitBreaker);
  }
}
