package lt.vu.tracker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IssueRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 5000) String description
) {}
