package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.response.HistoryEntryResponse;
import lt.vu.tracker.dto.response.HistoryPageResponse;
import lt.vu.tracker.dto.response.UserSummary;
import lt.vu.tracker.model.FieldChange;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.IssueHistory;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.HistoryAction;
import lt.vu.tracker.model.enums.IssueStatus;
import lt.vu.tracker.model.enums.ProjectRole;
import lt.vu.tracker.repository.IssueHistoryRepository;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueHistoryService {
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final IssueHistoryRepository historyRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    //  : called by IssueService after a change has been saved

    public void recordCreated(Issue issue, User actor) {
        List<FieldChange> changes = new ArrayList<>();
        changes.add(new FieldChange("title", null, issue.getTitle()));
        String description = normalise(issue.getDescription());
        if (description != null) {
            changes.add(new FieldChange("description", null, description));
        }
        changes.add(new FieldChange("status", null, statusName(issue.getStatus())));
        write(issue, HistoryAction.CREATED, changes, actor);
    }

    public void recordUpdated(Issue issue, String oldTitle, String oldDescription, User actor) {
        List<FieldChange> changes = new ArrayList<>();
        addIfChanged(changes, "title", oldTitle, issue.getTitle());
        addIfChanged(changes, "description", oldDescription, issue.getDescription());
        if (changes.isEmpty()) {
            return;
        }
        write(issue, HistoryAction.UPDATED, changes, actor);
    }

    public void recordTransition(Issue issue, IssueStatus from, IssueStatus to, User actor) {
        write(issue, HistoryAction.TRANSITIONED,
                List.of(new FieldChange("status", statusName(from), statusName(to))), actor);
    }

    public void recordDeleted(Issue issue, User actor) {
        write(issue, HistoryAction.DELETED,
                List.of(new FieldChange("title", issue.getTitle(), null)), actor);
    }

    //  reading

    // History of one issue, newest first. Any project member may read it.
    public List<HistoryEntryResponse> listForIssue(String key, User current) {
        String normalised = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        Issue issue = issueRepository.findByKey(normalised)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
        permissionService.requireRole(issue.getProjectId(), current.getId(), ProjectRole.VIEWER);
        return toResponses(historyRepository.findByIssueId(issue.getId(), NEWEST_FIRST));
    }

    // Project-wide activity feed, newest first, including issues that were deleted.
    public HistoryPageResponse listForProject(String projectId, int page, int size, User current) {
        permissionService.requireRole(projectId, current.getId(), ProjectRole.VIEWER);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<IssueHistory> result = historyRepository.findByProjectId(
                projectId, PageRequest.of(safePage, safeSize, NEWEST_FIRST));
        return new HistoryPageResponse(toResponses(result.getContent()), safePage, safeSize,
                result.getTotalElements(), result.getTotalPages());
    }

    //  helpers

    private void write(Issue issue, HistoryAction action, List<FieldChange> changes, User actor) {
        IssueHistory entry = new IssueHistory();
        entry.setIssueId(issue.getId());
        entry.setIssueKey(issue.getKey());
        entry.setProjectId(issue.getProjectId());
        entry.setActorId(actor.getId());
        entry.setAction(action);
        entry.setChanges(new ArrayList<>(changes));
        entry.setCreatedAt(Instant.now());
        historyRepository.insert(entry);     // insert, never save: history is append-only
    }

    private static void addIfChanged(List<FieldChange> changes, String field, String oldValue, String newValue) {
        String before = normalise(oldValue);
        String after = normalise(newValue);
        if (!Objects.equals(before, after)) {
            changes.add(new FieldChange(field, before, after));
        }
    }

    private static String normalise(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String statusName(IssueStatus status) {
        return status == null ? null : status.name();
    }

    private List<HistoryEntryResponse> toResponses(Collection<IssueHistory> entries) {
        Set<String> actorIds = entries.stream().map(IssueHistory::getActorId).collect(Collectors.toSet());
        Map<String, User> actors = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return entries.stream()
                .map(e -> new HistoryEntryResponse(e.getId(), e.getIssueKey(), e.getAction(),
                        UserSummary.from(actors.get(e.getActorId())), e.getChanges(), e.getCreatedAt()))
                .toList();
    }
}