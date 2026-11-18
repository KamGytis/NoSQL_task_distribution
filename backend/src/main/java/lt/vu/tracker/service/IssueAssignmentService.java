package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.Project;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.ProjectRole;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class IssueAssignmentService {
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final IssueHistoryService historyService;

    public IssueResponse assign(String key, String assigneeId, User current) {
        Issue issue = findIssue(key);
        Project project = permissionService.requireRole(issue.getProjectId(), current.getId(), ProjectRole.MEMBER);

        boolean eligible = permissionService.roleOf(project, assigneeId)
                .map(role -> role.atLeast(ProjectRole.MEMBER)).orElse(false);
        if (!eligible) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Assignee must be a project member who can edit issues");
        }
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee not found"));

        if (assigneeId.equals(issue.getAssigneeId())) {
            return IssueResponse.from(issue);     // already assigned to them: nothing to do
        }

        User previous = previousAssignee(issue);
        issue.setAssigneeId(assigneeId);
        Issue saved = issueRepository.save(issue);
        historyService.recordAssigned(saved, previous, assignee, current);
        notificationService.notifyAssigned(assignee, current, saved);
        return IssueResponse.from(saved);
    }

    public IssueResponse unassign(String key, User current) {
        Issue issue = findIssue(key);
        permissionService.requireRole(issue.getProjectId(), current.getId(), ProjectRole.MEMBER);

        if (issue.getAssigneeId() == null) {
            return IssueResponse.from(issue);
        }
        User previous = previousAssignee(issue);
        issue.setAssigneeId(null);
        Issue saved = issueRepository.save(issue);
        historyService.recordAssigned(saved, previous, null, current);
        return IssueResponse.from(saved);
    }

    private Issue findIssue(String key) {
        String normalised = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        return issueRepository.findByKey(normalised)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
    }

    private User previousAssignee(Issue issue) {
        return issue.getAssigneeId() == null
                ? null
                : userRepository.findById(issue.getAssigneeId()).orElse(null);
    }
}