package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.WorklogRequest;
import lt.vu.tracker.dto.response.UserSummary;
import lt.vu.tracker.dto.response.WorklogListResponse;
import lt.vu.tracker.dto.response.WorklogResponse;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.Project;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.Worklog;
import lt.vu.tracker.model.enums.ProjectRole;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.UserRepository;
import lt.vu.tracker.repository.WorklogRepository;
import lt.vu.tracker.util.TimeFormat;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorklogService {
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "startedAt", "createdAt");

    private final WorklogRepository worklogRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    public WorklogResponse add(String issueKey, WorklogRequest request, User current) {
        Issue issue = findIssue(issueKey);
        Project project = permissionService.requireRole(issue.getProjectId(), current.getId(), ProjectRole.MEMBER);
        Instant now = Instant.now();

        Worklog worklog = new Worklog();
        worklog.setIssueId(issue.getId());
        worklog.setIssueKey(issue.getKey());
        worklog.setProjectId(project.getId());
        worklog.setUserId(current.getId());
        worklog.setMinutes(request.minutes());
        worklog.setStartedAt(request.startedAt() == null ? now : checkNotInTheFuture(request.startedAt(), now));
        worklog.setNote(cleanNote(request.note()));
        worklog.setCreatedAt(now);

        Worklog saved = worklogRepository.save(worklog);
        return toResponse(saved, project, current, usersFor(List.of(saved)));
    }

    public WorklogListResponse list(String issueKey, User current) {
        Issue issue = findIssue(issueKey);
        Project project = permissionService.requireRole(issue.getProjectId(), current.getId(), ProjectRole.VIEWER);

        List<Worklog> worklogs = worklogRepository.findByIssueId(issue.getId(), NEWEST_FIRST);
        Map<String, User> users = usersFor(worklogs);
        List<WorklogResponse> entries = worklogs.stream()
                .map(w -> toResponse(w, project, current, users))
                .toList();
        long total = worklogs.stream().mapToLong(Worklog::getMinutes).sum();
        return new WorklogListResponse(total, TimeFormat.minutes(total), entries);
    }

    public WorklogResponse update(String worklogId, WorklogRequest request, User current) {
        Worklog worklog = findWorklog(worklogId);
        Project project = permissionService.requireRole(worklog.getProjectId(), current.getId(), ProjectRole.MEMBER);
        if (!worklog.getUserId().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author can edit a worklog");
        }

        Instant now = Instant.now();
        worklog.setMinutes(request.minutes());
        if (request.startedAt() != null) {
            worklog.setStartedAt(checkNotInTheFuture(request.startedAt(), now));
        }
        worklog.setNote(cleanNote(request.note()));
        worklog.setUpdatedAt(now);

        Worklog saved = worklogRepository.save(worklog);
        return toResponse(saved, project, current, usersFor(List.of(saved)));
    }

    // The author can delete their own worklog, and a project admin can delete any worklog
    public void delete(String worklogId, User current) {
        Worklog worklog = findWorklog(worklogId);
        Project project = permissionService.requireRole(worklog.getProjectId(), current.getId(), ProjectRole.VIEWER);
        boolean author = worklog.getUserId().equals(current.getId());
        boolean admin = permissionService.roleOf(project, current.getId())
                .map(role -> role == ProjectRole.ADMIN).orElse(false);
        if (!author && !admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the author or a project admin can delete a worklog");
        }
        worklogRepository.delete(worklog);
    }

    //  helpers

    private Issue findIssue(String key) {
        String normalised = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        return issueRepository.findByKey(normalised)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
    }

    private Worklog findWorklog(String id) {
        return worklogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Worklog not found"));
    }

    private Instant checkNotInTheFuture(Instant startedAt, Instant now) {
        if (startedAt.isAfter(now.plus(CLOCK_SKEW))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work cannot start in the future");
        }
        return startedAt;
    }

    private String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }

    private Map<String, User> usersFor(Collection<Worklog> worklogs) {
        Set<String> ids = worklogs.stream().map(Worklog::getUserId).collect(Collectors.toSet());
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private WorklogResponse toResponse(Worklog w, Project project, User current, Map<String, User> users) {
        ProjectRole role = permissionService.roleOf(project, current.getId()).orElse(null);
        boolean author = w.getUserId().equals(current.getId());
        boolean canEdit = author && role != null && role.atLeast(ProjectRole.MEMBER);
        boolean canDelete = author || role == ProjectRole.ADMIN;
        return new WorklogResponse(w.getId(), w.getIssueKey(), UserSummary.from(users.get(w.getUserId())),
                w.getMinutes(), TimeFormat.minutes(w.getMinutes()), w.getStartedAt(), w.getNote(),
                w.getCreatedAt(), w.getUpdatedAt(), canEdit, canDelete);
    }
}