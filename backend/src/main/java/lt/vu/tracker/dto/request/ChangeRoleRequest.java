package lt.vu.tracker.dto.request;

import jakarta.validation.constraints.NotNull;
import lt.vu.tracker.model.enums.ProjectRole;

public record ChangeRoleRequest(@NotNull ProjectRole role) {}