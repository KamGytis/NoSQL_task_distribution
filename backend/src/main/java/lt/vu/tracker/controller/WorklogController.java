package lt.vu.tracker.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.WorklogRequest;
import lt.vu.tracker.dto.response.WorklogListResponse;
import lt.vu.tracker.dto.response.WorklogResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.WorklogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorklogController {
    private final WorklogService worklogService;
    private final CurrentUser currentUser;

    @PostMapping("/issues/{key}/worklogs")
    @ResponseStatus(HttpStatus.CREATED)
    public WorklogResponse add(@PathVariable String key, @Valid @RequestBody WorklogRequest request) {
        return worklogService.add(key, request, currentUser.get());
    }

    @GetMapping("/issues/{key}/worklogs")
    public WorklogListResponse list(@PathVariable String key) {
        return worklogService.list(key, currentUser.get());
    }

    @PutMapping("/worklogs/{id}")
    public WorklogResponse update(@PathVariable String id, @Valid @RequestBody WorklogRequest request) {
        return worklogService.update(id, request, currentUser.get());
    }

    @DeleteMapping("/worklogs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        worklogService.delete(id, currentUser.get());
    }
}