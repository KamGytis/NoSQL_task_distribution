package lt.vu.tracker.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.IssueRequest;
import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.IssueService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
}