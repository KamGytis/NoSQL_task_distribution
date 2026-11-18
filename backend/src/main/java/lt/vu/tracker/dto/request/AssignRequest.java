package lt.vu.tracker.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AssignRequest(@NotBlank String assigneeId) {}