package com.acme.weeklycommit.integration.rcdo;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Builds the {@link WebClient} that {@link RcdoClient} uses. Wires the configured base URL,
 * read/connect timeouts, and the service-token bearer header.
 *
 * <p>Auth header sourcing: {@code RCDO_SERVICE_TOKEN} env var. Empty in dev/test (WireMock does not
 * validate); must be set in prod.
 */
@Configuration
class RcdoWebClientConfig {

  @Bean
  WebClient rcdoWebClient(
      @Value("${weekly-commit.rcdo.base-url}") String baseUrl,
      @Value("${weekly-commit.rcdo.timeout-ms:2000}") long timeoutMs,
      @Value("${weekly-commit.rcdo.service-token:}") String serviceToken) {
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
  RcdoClient rcdoClient(
      WebClient rcdoWebClient, @Value("${weekly-commit.rcdo.timeout-ms:2000}") long timeoutMs) {
    return new RcdoClient(rcdoWebClient, Duration.ofMillis(timeoutMs));
  }
}
