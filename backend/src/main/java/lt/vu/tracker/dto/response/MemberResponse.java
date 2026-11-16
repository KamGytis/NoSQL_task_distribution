package lt.vu.tracker.dto.response;

import lt.vu.tracker.model.enums.ProjectRole;

public record MemberResponse(String userId, String firstName, String lastName,
                             String email, ProjectRole role) {}