package lt.vu.tracker.dto.response;

import lt.vu.tracker.model.Sprint;
import lt.vu.tracker.model.enums.SprintStatus;

import java.time.Instant;

public record SprintResponse(
        String id,
        String projectId,
        String name,
        SprintStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
    public static SprintResponse from(Sprint sprint) {
        return new SprintResponse(sprint.getId(), sprint.getProjectId(), sprint.getName(),
                sprint.getStatus(), sprint.getCreatedAt(), sprint.getStartedAt(), sprint.getCompletedAt());
    }
}