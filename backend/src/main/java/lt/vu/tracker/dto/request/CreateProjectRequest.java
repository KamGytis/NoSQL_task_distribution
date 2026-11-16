package lt.vu.tracker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$",
                message = "Key must be 2-10 uppercase letters/digits, starting with a letter") String key,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description) {}