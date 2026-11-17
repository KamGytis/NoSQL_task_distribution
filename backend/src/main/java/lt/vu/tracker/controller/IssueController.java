package lt.vu.tracker.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.IssueRequest;
import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.IssueService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import lt.vu.tracker.dto.request.IssueTransitionRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IssueController {
    private final IssueService issueService;
    private final CurrentUser currentUser;

    @PostMapping("/projects/{projectId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public IssueResponse create(
            @PathVariable String projectId,
            @Valid @RequestBody IssueRequest request
    ) {
        return issueService.create(projectId, request, currentUser.get());
    }
    @GetMapping("/projects/{projectId}/issues")
    public List<IssueResponse> list(@PathVariable String projectId) {
        return issueService.list(projectId, currentUser.get());
    }

    @GetMapping("/issues/{key}")
    public IssueResponse get(@PathVariable String key) {
        return issueService.get(key, currentUser.get());
    }

    @PutMapping("/issues/{key}")
    public IssueResponse update(
            @PathVariable String key,
            @Valid @RequestBody IssueRequest request
    ) {
        return issueService.update(key, request, currentUser.get());
    }

    @PostMapping("/issues/{key}/transition")
    public IssueResponse transition(
            @PathVariable String key,
            @Valid @RequestBody IssueTransitionRequest request
    ) {
        return issueService.transition(key, request.status(), currentUser.get());
    }

    @DeleteMapping("/issues/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String key) {
        issueService.delete(key, currentUser.get());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleIssueConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Issue changed. Reload it and try again."));
    }
}