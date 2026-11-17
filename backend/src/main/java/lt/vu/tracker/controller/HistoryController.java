package lt.vu.tracker.controller;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.response.HistoryEntryResponse;
import lt.vu.tracker.dto.response.HistoryPageResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.IssueHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HistoryController {
    private final IssueHistoryService historyService;
    private final CurrentUser currentUser;

    @GetMapping("/issues/{key}/history")
    public List<HistoryEntryResponse> issueHistory(@PathVariable String key) {
        return historyService.listForIssue(key, currentUser.get());
    }

    @GetMapping("/projects/{projectId}/activity")
    public HistoryPageResponse projectActivity(@PathVariable String projectId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return historyService.listForProject(projectId, page, size, currentUser.get());
    }
}