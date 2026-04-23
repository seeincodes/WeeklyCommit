package com.acme.weeklycommit.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiEnvelopeTest {

  @Test
  void of_addsNowToMeta() {
    ApiEnvelope<String> env = ApiEnvelope.of("hello");
    assertThat(env.data()).isEqualTo("hello");
    assertThat(env.meta()).containsKey("now");
    // `now` parses as an Instant
    assertThat((Object) Instant.parse((String) env.meta().get("now"))).isNotNull();
  }

  @Test
  void of_withExtraMeta_merges() {
    ApiEnvelope<Integer> env = ApiEnvelope.of(42, Map.of("totalCount", 100));
    assertThat(env.meta()).containsKey("now").containsEntry("totalCount", 100);
  }
}
