package com.acme.weeklycommit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.NotificationDlt;
import com.acme.weeklycommit.domain.enums.PlanState;
import com.acme.weeklycommit.integration.notification.NotificationSender;
import com.acme.weeklycommit.repo.NotificationDltRepository;
import com.acme.weeklycommit.service.statemachine.NotificationEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class DltReplayServiceTest {

  @Mock private NotificationDltRepository dltRepo;
  @Mock private NotificationSender sender;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private DltReplayService service() {
    return new DltReplayService(dltRepo, sender, objectMapper);
  }

  @Test
  void replay_throwsAccessDenied_whenNotAdmin() {
    UUID dltId = UUID.randomUUID();

    assertThatThrownBy(() -> service().replay(dltId, principal(Set.of())))
        .isInstanceOf(AccessDeniedException.class);

    verify(dltRepo, never()).findById(any());
    verify(sender, never()).send(any());
  }

  @Test
  void replay_throwsAccessDenied_whenManagerOnly() {
    UUID dltId = UUID.randomUUID();

    assertThatThrownBy(() -> service().replay(dltId, principal(Set.of("MANAGER"))))
        .isInstanceOf(AccessDeniedException.class);

    verify(dltRepo, never()).findById(any());
  }

  @Test
  void replay_throwsResourceNotFound_whenDltRowMissing() {
    UUID dltId = UUID.randomUUID();
    when(dltRepo.findById(dltId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service().replay(dltId, principal(Set.of("ADMIN"))))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(sender, never()).send(any());
    verify(dltRepo, never()).delete(any());
  }

  @Test
  void replay_happyPath_sendsAndDeletes() throws Exception {
    UUID dltId = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    NotificationEvent original =
        new NotificationEvent(planId, PlanState.DRAFT, PlanState.LOCKED, 3L);
    JsonNode payload = objectMapper.valueToTree(original);
    NotificationDlt row =
        new NotificationDlt(dltId, "PLAN_LOCKED", payload, "503 from notification-svc", 5);
    when(dltRepo.findById(dltId)).thenReturn(Optional.of(row));

    service().replay(dltId, principal(Set.of("ADMIN")));

    ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
    verify(sender).send(captor.capture());
    assertThat(captor.getValue()).isEqualTo(original);
    verify(dltRepo).delete(row);
  }

  @Test
  void replay_whenSenderThrows_doesNotDeleteRow() {
    UUID dltId = UUID.randomUUID();
    UUID planId = UUID.randomUUID();
    NotificationEvent original =
        new NotificationEvent(planId, PlanState.LOCKED, PlanState.RECONCILED, 4L);
    JsonNode payload = objectMapper.valueToTree(original);
    NotificationDlt row = new NotificationDlt(dltId, "PLAN_RECONCILED", payload, "boom", 5);
    when(dltRepo.findById(dltId)).thenReturn(Optional.of(row));
    org.mockito.Mockito.doThrow(new RuntimeException("notification-svc 502"))
        .when(sender)
        .send(any());

    assertThatThrownBy(() -> service().replay(dltId, principal(Set.of("ADMIN"))))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("notification-svc 502");

    verify(dltRepo, never()).delete(any());
  }

  @Test
  void replay_whenPayloadCannotBeDeserialized_throwsIllegalState() {
    UUID dltId = UUID.randomUUID();
    JsonNode garbage = objectMapper.valueToTree(Map.of("not", "an event"));
    NotificationDlt row = new NotificationDlt(dltId, "UNKNOWN", garbage, "boom", 5);
    when(dltRepo.findById(dltId)).thenReturn(Optional.of(row));

    assertThatThrownBy(() -> service().replay(dltId, principal(Set.of("ADMIN"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(dltId.toString());

    verify(sender, never()).send(any());
    verify(dltRepo, never()).delete(any());
  }

  private static AuthenticatedPrincipal principal(Set<String> roles) {
    UUID id = UUID.randomUUID();
    Jwt jwt =
        new Jwt(
            "t",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", id.toString(),
                "org_id", UUID.randomUUID().toString(),
                "timezone", "UTC",
                "roles", List.copyOf(roles)));
    return new AuthenticatedPrincipal(
        id, UUID.randomUUID(), Optional.empty(), roles, ZoneId.of("UTC"), jwt);
  }
}
