package lt.vu.tracker.dto.response;

import java.time.Instant;
import java.util.List;

public record CommentResponse(String id, String issueKey, UserSummary author, String text,
                              List<UserSummary> mentions, Instant createdAt, Instant editedAt,
                              boolean canEdit, boolean canDelete) {}