package lt.vu.tracker.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AssignSprintRequest(@NotBlank String sprintId) {}