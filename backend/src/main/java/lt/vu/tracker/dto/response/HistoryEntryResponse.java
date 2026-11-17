package lt.vu.tracker.dto.response;

import lt.vu.tracker.model.FieldChange;
import lt.vu.tracker.model.enums.HistoryAction;

import java.time.Instant;
import java.util.List;

public record HistoryEntryResponse(String id, String issueKey, HistoryAction action,
                                   UserSummary actor, List<FieldChange> changes, Instant createdAt) {}