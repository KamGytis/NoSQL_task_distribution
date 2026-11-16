package lt.vu.tracker.dto.response;

import lt.vu.tracker.model.enums.InvitationStatus;
import lt.vu.tracker.model.enums.ProjectRole;

import java.time.Instant;

public record InvitationResponse(String id, String projectId, String projectName,
                                 ProjectRole role, InvitationStatus status,
                                 String invitedByName, Instant createdAt) {}