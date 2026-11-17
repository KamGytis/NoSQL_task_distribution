package lt.vu.tracker.dto.response;

import lt.vu.tracker.model.enums.ProjectRole;

import java.util.List;

public record BoardResponse(String projectId, String projectKey, String projectName,
                            ProjectRole myRole, List<BoardColumnResponse> columns) {}