package lt.vu.tracker.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.AssignSprintRequest;
import lt.vu.tracker.dto.request.CreateSprintRequest;
import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.dto.response.SprintResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.SprintService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SprintController {
    private final SprintService sprints;
    private final CurrentUser currentUser;

    @PostMapping("/projects/{projectId}/sprints")
    @ResponseStatus(HttpStatus.CREATED)
    public SprintResponse create(@PathVariable String projectId,
                                 @Valid @RequestBody CreateSprintRequest request) {
        return sprints.create(projectId, request, currentUser.get());
    }

    @GetMapping("/projects/{projectId}/sprints")
    public List<SprintResponse> list(@PathVariable String projectId) {
        return sprints.list(projectId, currentUser.get());
    }

    @GetMapping("/projects/{projectId}/backlog")
    public List<IssueResponse> backlog(@PathVariable String projectId) {
        return sprints.backlog(projectId, currentUser.get());
    }

    @GetMapping("/sprints/{sprintId}/issues")
    public List<IssueResponse> sprintIssues(@PathVariable String sprintId) {
        return sprints.sprintIssues(sprintId, currentUser.get());
    }

    @PutMapping("/issues/{key}/sprint")
    public IssueResponse assign(@PathVariable String key, @Valid @RequestBody AssignSprintRequest request) {
        return sprints.assign(key, request.sprintId(), currentUser.get());
    }

    @DeleteMapping("/issues/{key}/sprint")
    public IssueResponse moveToBacklog(@PathVariable String key) {
        return sprints.moveToBacklog(key, currentUser.get());
    }

    @PostMapping("/sprints/{sprintId}/start")
    public SprintResponse start(@PathVariable String sprintId) {
        return sprints.start(sprintId, currentUser.get());
    }

    @PostMapping("/sprints/{sprintId}/complete")
    public SprintResponse complete(@PathVariable String sprintId) {
        return sprints.complete(sprintId, currentUser.get());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Sprint or issue changed. Reload it and try again."));
    }
}