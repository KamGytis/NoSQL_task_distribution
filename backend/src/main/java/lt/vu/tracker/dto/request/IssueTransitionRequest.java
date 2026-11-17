package lt.vu.tracker.dto.request;

import jakarta.validation.constraints.NotNull;
import lt.vu.tracker.model.enums.IssueStatus;

public record IssueTransitionRequest(@NotNull IssueStatus status) {}
