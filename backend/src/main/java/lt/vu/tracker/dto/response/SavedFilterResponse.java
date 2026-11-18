package lt.vu.tracker.dto.response;

import lt.vu.tracker.model.SavedFilter;
import lt.vu.tracker.model.enums.IssueStatus;

import java.time.Instant;

public record SavedFilterResponse(
        String id,
        String projectId,
        String name,
        IssueStatus status,
        String assigneeId,
        String titleContains,
        Instant createdAt,
        Instant updatedAt
) {
    public static SavedFilterResponse from(SavedFilter filter) {
        return new SavedFilterResponse(
                filter.getId(),
                filter.getProjectId(),
                filter.getName(),
                filter.getStatus(),
                filter.getAssigneeId(),
                filter.getTitleContains(),
                filter.getCreatedAt(),
                filter.getUpdatedAt()
        );
    }
}