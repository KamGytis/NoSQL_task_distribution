package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.CreateSprintRequest;
import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.dto.response.SprintResponse;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.Sprint;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.ProjectRole;
import lt.vu.tracker.model.enums.SprintStatus;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.SprintRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SprintService {
    private final SprintRepository sprints;
    private final IssueRepository issues;
    private final PermissionService permissions;
    private final SprintLock lock;
    private final ProjectReadCache projectReadCache;

    public SprintResponse create(String projectId, CreateSprintRequest request, User current) {
        permissions.requireRole(projectId, current.getId(), ProjectRole.ADMIN);
        Sprint sprint = new Sprint();
        sprint.setProjectId(projectId);
        sprint.setName(request.name().trim());
        sprint.setStatus(SprintStatus.PLANNED);
        sprint.setCreatedAt(Instant.now());
        return SprintResponse.from(sprints.insert(sprint));
    }

    public List<SprintResponse> list(String projectId, User current) {
        permissions.requireRole(projectId, current.getId(), ProjectRole.VIEWER);
        return sprints.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream().map(SprintResponse::from).toList();
    }

    public List<IssueResponse> backlog(String projectId, User current) {
        permissions.requireRole(projectId, current.getId(), ProjectRole.VIEWER);

        return projectReadCache.read(projectId, "backlog", IssueResponse[].class,
                () -> issues.findByProjectIdAndSprintIdIsNullOrderByCreatedAtDesc(projectId)
                        .stream()
                        .map(IssueResponse::from)
                        .toList());
    }

    public List<IssueResponse> sprintIssues(String sprintId, User current) {
        Sprint sprint = requireSprint(sprintId);
        permissions.requireRole(sprint.getProjectId(), current.getId(), ProjectRole.VIEWER);
        return issues.findByProjectIdAndSprintIdOrderByCreatedAtDesc(sprint.getProjectId(), sprintId)
                .stream().map(IssueResponse::from).toList();
    }

    public IssueResponse assign(String issueKey, String sprintId, User current) {
        String projectId = requireIssue(issueKey).getProjectId();
        permissions.requireRole(projectId, current.getId(), ProjectRole.MEMBER);

        return lock.withLock(projectId, () -> {
            Issue issue = requireIssue(issueKey);
            Sprint target = requireSprint(sprintId);
            if (!target.getProjectId().equals(projectId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found in this project");
            }
            if (Objects.equals(issue.getSprintId(), sprintId)) {
                return IssueResponse.from(issue);
            }
            if (target.getStatus() == SprintStatus.COMPLETED) {
                throw conflict("Cannot add an issue to a completed sprint");
            }
            issue.setSprintId(sprintId);
            Issue saved = issues.save(issue);
            projectReadCache.invalidate(saved.getProjectId());
            return IssueResponse.from(saved);
        });
    }

    public IssueResponse moveToBacklog(String issueKey, User current) {
        String projectId = requireIssue(issueKey).getProjectId();
        permissions.requireRole(projectId, current.getId(), ProjectRole.MEMBER);

        return lock.withLock(projectId, () -> {
            Issue issue = requireIssue(issueKey);
            if (issue.getSprintId() == null) {
                return IssueResponse.from(issue);
            }
            issue.setSprintId(null);
            Issue saved = issues.save(issue);
            projectReadCache.invalidate(saved.getProjectId());
            return IssueResponse.from(saved);
        });
    }

    public SprintResponse start(String sprintId, User current) {
        String projectId = requireSprint(sprintId).getProjectId();
        permissions.requireRole(projectId, current.getId(), ProjectRole.ADMIN);

        return lock.withLock(projectId, () -> {
            Sprint sprint = requireSprint(sprintId);
            if (sprint.getStatus() != SprintStatus.PLANNED) {
                throw conflict("Only a planned sprint can be started");
            }
            if (sprints.existsByProjectIdAndStatus(projectId, SprintStatus.ACTIVE)) {
                throw conflict("Project already has an active sprint");
            }
            sprint.setStatus(SprintStatus.ACTIVE);
            sprint.setStartedAt(Instant.now());
            try {
                return SprintResponse.from(sprints.save(sprint));
            } catch (DuplicateKeyException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Project already has an active sprint", exception);
            }
        });
    }

    public SprintResponse complete(String sprintId, User current) {
        String projectId = requireSprint(sprintId).getProjectId();
        permissions.requireRole(projectId, current.getId(), ProjectRole.ADMIN);

        return lock.withLock(projectId, () -> {
            Sprint sprint = requireSprint(sprintId);
            if (sprint.getStatus() != SprintStatus.ACTIVE) {
                throw conflict("Only an active sprint can be completed");
            }
            sprint.setStatus(SprintStatus.COMPLETED);
            sprint.setCompletedAt(Instant.now());
            return SprintResponse.from(sprints.save(sprint));
        });
    }

    private Sprint requireSprint(String sprintId) {
        return sprints.findById(sprintId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Sprint not found"));
    }

    private Issue requireIssue(String issueKey) {
        return issues.findByKey(issueKey).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}