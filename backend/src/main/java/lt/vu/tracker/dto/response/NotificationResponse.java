package lt.vu.tracker.dto.response;

import lt.vu.tracker.model.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(String id, NotificationType type, UserSummary actor, String issueKey,
                                   String commentId, String projectId, String message,
                                   boolean read, Instant createdAt) {}