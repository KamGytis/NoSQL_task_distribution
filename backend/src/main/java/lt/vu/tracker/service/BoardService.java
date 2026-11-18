package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.response.BoardColumnResponse;
import lt.vu.tracker.dto.response.BoardResponse;
import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.model.Project;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.IssueStatus;
import lt.vu.tracker.model.enums.ProjectRole;
import lt.vu.tracker.repository.IssueRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BoardService {
    private final IssueRepository issueRepository;
    private final PermissionService permissionService;
    private final ProjectReadCache projectReadCache;

    public BoardResponse get(String projectId, User current) {
        Project project = permissionService.requireRole(
                projectId, current.getId(), ProjectRole.VIEWER
        );

        List<BoardColumnResponse> columns = projectReadCache.read(
                projectId, "board", BoardColumnResponse[].class, () -> loadColumns(projectId)
        );

        ProjectRole myRole = permissionService.roleOf(project, current.getId()).orElse(null);
        return new BoardResponse(project.getId(), project.getKey(), project.getName(), myRole, columns);
    }

    private List<BoardColumnResponse> loadColumns(String projectId) {
        Map<IssueStatus, List<IssueResponse>> byStatus = new EnumMap<>(IssueStatus.class);
        for (IssueStatus status : IssueStatus.values()) {
            byStatus.put(status, new ArrayList<>());
        }

        issueRepository.findByProjectIdOrderByCreatedAtDesc(projectId).forEach(issue -> {
            IssueStatus status = issue.getStatus() == null ? IssueStatus.TODO : issue.getStatus();
            byStatus.get(status).add(IssueResponse.from(issue));
        });

        List<BoardColumnResponse> columns = new ArrayList<>();
        for (IssueStatus status : IssueStatus.values()) {
            List<IssueResponse> issues = byStatus.get(status);
            columns.add(new BoardColumnResponse(status, status.getLabel(),
                    List.copyOf(status.nextStatuses()), issues.size(), issues));
        }
        return columns;
    }
}