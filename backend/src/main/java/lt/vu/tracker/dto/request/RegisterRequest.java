package lt.vu.tracker.dto.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull @Min(1900) Integer birthYear,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password) {}