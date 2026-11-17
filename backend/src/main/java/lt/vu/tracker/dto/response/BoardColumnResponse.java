package lt.vu.tracker.dto.response;

import lt.vu.tracker.model.enums.IssueStatus;

import java.util.List;

public record BoardColumnResponse(IssueStatus status, String title, List<IssueStatus> movesTo,
                                  int count, List<IssueResponse> issues) {}