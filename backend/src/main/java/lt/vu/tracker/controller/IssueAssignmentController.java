package lt.vu.tracker.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.AssignRequest;
import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.IssueAssignmentService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IssueAssignmentController {
    private final IssueAssignmentService assignmentService;
    private final CurrentUser currentUser;

    @PutMapping("/issues/{key}/assignee")
    public IssueResponse assign(@PathVariable String key, @Valid @RequestBody AssignRequest request) {
        return assignmentService.assign(key, request.assigneeId(), currentUser.get());
    }

    @DeleteMapping("/issues/{key}/assignee")
    public IssueResponse unassign(@PathVariable String key) {
        return assignmentService.unassign(key, currentUser.get());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Issue changed. Reload it and try again."));
    }
}