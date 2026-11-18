package lt.vu.tracker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lt.vu.tracker.model.enums.IssueStatus;

public record SavedFilterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String projectId,
        IssueStatus status,
        @Pattern(regexp = "\\S+") @Size(max = 100) String assigneeId,
        @Size(max = 200) String titleContains
) {}