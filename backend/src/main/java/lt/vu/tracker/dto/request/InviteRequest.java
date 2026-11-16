package lt.vu.tracker.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lt.vu.tracker.model.enums.ProjectRole;

public record InviteRequest(@NotBlank @Email String email, @NotNull ProjectRole role) {}