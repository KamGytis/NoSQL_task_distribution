package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.IssueRequest;
import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.Project;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.IssueStatus;
import lt.vu.tracker.model.enums.ProjectRole;
import lt.vu.tracker.repository.IssueRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueService {
    private final IssueRepository issueRepository;
    private final PermissionService permissionService;
    private final StringRedisTemplate redisTemplate;

    public IssueResponse create(String projectId, IssueRequest request, User current) {
        Project project = permissionService.requireRole(
                projectId, current.getId(), ProjectRole.MEMBER
        );

        String counterKey = "tracker:issue-counter:" + project.getKey();
        Long number = redisTemplate.opsForValue().increment(counterKey);

        if (number == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Issue counter unavailable"
            );
        }

        Issue issue = new Issue();
        issue.setKey(project.getKey() + "-" + number);
        issue.setProjectId(project.getId());
        issue.setTitle(request.title().trim());
        issue.setDescription(request.description());
        issue.setStatus(IssueStatus.TODO);
        issue.setReporterId(current.getId());
        issue.setCreatedAt(Instant.now());

        try {
            return IssueResponse.from(issueRepository.insert(issue));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Generated issue key already exists", exception
            );
        }
    }
    public List<IssueResponse> list(String projectId, User current) {
        permissionService.requireRole(projectId, current.getId(), ProjectRole.VIEWER);

        return issueRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(IssueResponse::from)
                .toList();
    }

    public IssueResponse get(String key, User current) {
        return IssueResponse.from(requireIssue(key, current, ProjectRole.VIEWER));
    }

    public IssueResponse update(String key, IssueRequest request, User current) {
        Issue issue = requireIssue(key, current, ProjectRole.MEMBER);
        issue.setTitle(request.title().trim());
        issue.setDescription(request.description());

        return IssueResponse.from(issueRepository.save(issue));
    }

    public IssueResponse transition(String key, IssueStatus status, User current) {
        Issue issue = requireIssue(key, current, ProjectRole.MEMBER);
        IssueStatus from = issue.getStatus();

        if (from == status) {
            return IssueResponse.from(issue);
        }
        if (!from.canMoveTo(status)) {
            String allowed = from.nextStatuses().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot move an issue from " + from + " to " + status + ". Allowed: " + allowed);
        }

        issue.setStatus(status);
        return IssueResponse.from(issueRepository.save(issue));
    }

    public void delete(String key, User current) {
        Issue issue = requireIssue(key, current, ProjectRole.ADMIN);
        issueRepository.delete(issue);
    }

    private Issue requireIssue(String key, User current, ProjectRole minimumRole) {
        Issue issue = issueRepository.findByKey(key)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Issue not found"
                ));

        permissionService.requireRole(issue.getProjectId(), current.getId(), minimumRole);
        return issue;
    }
}