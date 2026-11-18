package lt.vu.tracker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSprintRequest(@NotBlank @Size(max = 100) String name) {}