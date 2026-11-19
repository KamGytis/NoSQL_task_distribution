package lt.vu.tracker.controller;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.dto.response.IssueSuggestionResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
public class SearchController {
    private final SearchService search;
    private final CurrentUser currentUser;

    @GetMapping("/search")
    public List<IssueResponse> search(
            @PathVariable String projectId,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return search.search(projectId, q, page, size, currentUser.get());
    }

    @GetMapping("/suggest")
    public List<IssueSuggestionResponse> suggest(
            @PathVariable String projectId,
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int size
    ) {
        return search.suggest(projectId, q, size, currentUser.get());
    }
}