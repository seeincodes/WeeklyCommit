package com.acme.weeklycommit.service;

import com.acme.weeklycommit.api.exception.ResourceNotFoundException;
import com.acme.weeklycommit.config.AuthenticatedPrincipal;
import com.acme.weeklycommit.domain.entity.NotificationDlt;
import com.acme.weeklycommit.integration.notification.NotificationSender;
import com.acme.weeklycommit.repo.NotificationDltRepository;
import com.acme.weeklycommit.service.statemachine.NotificationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replays a single dead-letter notification row by re-issuing the original {@link
 * NotificationEvent} through {@link NotificationSender} and deleting the DLT row on success.
 *
 * <p><b>Authz:</b> ADMIN only. Defense-in-depth: SecurityConfig already gates {@code
 * /api/v1/admin/**} at the filter chain.
 *
 * <p><b>Payload contract:</b> {@code dlt.payload} is the JSON serialization of a {@link
 * NotificationEvent}. The future {@code NotificationClient} (group 7) must obey this contract when
 * writing DLT rows. A row whose payload cannot deserialize is a data-integrity failure, not a user
 * error — surfaced as {@link IllegalStateException} (500) so it shows up in alerts.
 *
 * <p><b>Failure semantics:</b> If {@link NotificationSender#send} throws, the DLT row stays put and
 * the exception propagates. The transaction rolls back, so the replay is retryable. The row is
 * deleted only after a successful send.
 */
@Service
public class DltReplayService {

  private final NotificationDltRepository dltRepo;
  private final NotificationSender notificationSender;
  private final ObjectMapper objectMapper;

  public DltReplayService(
      NotificationDltRepository dltRepo,
      NotificationSender notificationSender,
      ObjectMapper objectMapper) {
    this.dltRepo = dltRepo;
    this.notificationSender = notificationSender;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void replay(UUID dltId, AuthenticatedPrincipal caller) {
    if (!caller.hasRole("ADMIN")) {
      throw new AccessDeniedException(
          "caller " + caller.employeeId() + " requires ADMIN role to replay DLT row " + dltId);
    }
    NotificationDlt row =
        dltRepo
            .findById(dltId)
            .orElseThrow(() -> new ResourceNotFoundException("NotificationDlt", dltId));
    NotificationEvent event = deserialize(row);
    notificationSender.send(event);
    dltRepo.delete(row);
  }

  private NotificationEvent deserialize(NotificationDlt row) {
    try {
      return objectMapper.treeToValue(row.getPayload(), NotificationEvent.class);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      throw new IllegalStateException(
          "DLT row "
              + row.getId()
              + " payload is not a valid NotificationEvent — refusing to replay",
          e);
    }
  }
}
