package lt.vu.tracker.dto.response;

import java.time.Instant;

public record WorklogResponse(String id, String issueKey, UserSummary author, int minutes, String timeSpent,
                              Instant startedAt, String note, Instant createdAt, Instant updatedAt,
                              boolean canEdit, boolean canDelete) {}