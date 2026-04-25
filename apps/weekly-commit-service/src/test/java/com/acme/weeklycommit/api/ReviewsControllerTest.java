package com.acme.weeklycommit.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.weeklycommit.api.dto.ManagerReviewMapper;
import com.acme.weeklycommit.api.dto.ManagerReviewResponse;
import com.acme.weeklycommit.domain.entity.ManagerReview;
import com.acme.weeklycommit.service.ManagerReviewService;
import com.acme.weeklycommit.testsupport.WebMvcTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReviewsController.class)
@Import(WebMvcTestConfig.class)
@ActiveProfiles("test")
class ReviewsControllerTest {

  private static final UUID EMPLOYEE_ID =
      UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  @Autowired private MockMvc mvc;

  @MockBean private ManagerReviewService reviewService;
  @MockBean private ManagerReviewMapper mapper;

  private static JwtRequestPostProcessor validJwt() {
    return jwt().jwt(b -> b.subject(EMPLOYEE_ID.toString()).claim("org_id", ORG_ID.toString()));
  }

  @Test
  void createReview_201WithEnvelope() throws Exception {
    UUID planId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    UUID managerId = UUID.randomUUID();
    Instant ack = Instant.parse("2026-05-05T10:00:00Z");
    ManagerReview saved = new ManagerReview(reviewId, planId, managerId, ack);
    saved.setComment("nice");
    when(reviewService.createReview(any(), any(), any())).thenReturn(saved);
    when(mapper.toResponse(saved))
        .thenReturn(new ManagerReviewResponse(reviewId, planId, managerId, "nice", ack));

    mvc.perform(
            post("/api/v1/plans/" + planId + "/reviews")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"nice\"}")
                .with(validJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(reviewId.toString()))
        .andExpect(jsonPath("$.data.comment").value("nice"));
  }

  @Test
  void createReview_400OnOversizedComment() throws Exception {
    UUID planId = UUID.randomUUID();
    String oversized = "x".repeat(5001);

    mvc.perform(
            post("/api/v1/plans/" + planId + "/reviews")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"" + oversized + "\"}")
                .with(validJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void createReview_unauthenticated_returns401() throws Exception {
    UUID planId = UUID.randomUUID();
    mvc.perform(
            post("/api/v1/plans/" + planId + "/reviews")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listReviews_200WithEnvelopeArray() throws Exception {
    UUID planId = UUID.randomUUID();
    ManagerReview r =
        new ManagerReview(
            UUID.randomUUID(), planId, UUID.randomUUID(), Instant.parse("2026-05-05T10:00:00Z"));
    when(reviewService.listReviews(any(), any())).thenReturn(List.of(r));
    when(mapper.toResponse(r))
        .thenReturn(
            new ManagerReviewResponse(
                r.getId(), planId, r.getManagerId(), null, r.getAcknowledgedAt()));

    mvc.perform(get("/api/v1/plans/" + planId + "/reviews").with(validJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(r.getId().toString()));
  }

  @Test
  void listReviews_unauthenticated_returns401() throws Exception {
    UUID planId = UUID.randomUUID();
    mvc.perform(get("/api/v1/plans/" + planId + "/reviews"))
        .andExpect(status().isUnauthorized());
  }
}
