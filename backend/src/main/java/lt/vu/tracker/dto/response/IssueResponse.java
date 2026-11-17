package lt.vu.tracker.dto.response;

import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.enums.IssueStatus;

import java.time.Instant;

public record IssueResponse(
        String id,
        String key,
        String projectId,
        String title,
        String description,
        IssueStatus status,
        String reporterId,
        Instant createdAt
) {
    public static IssueResponse from(Issue issue) {
        return new IssueResponse(
                issue.getId(),
                issue.getKey(),
                issue.getProjectId(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getStatus(),
                issue.getReporterId(),
                issue.getCreatedAt()
        );
    }
}