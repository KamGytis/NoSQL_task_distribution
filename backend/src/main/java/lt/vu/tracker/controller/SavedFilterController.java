package lt.vu.tracker.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.SavedFilterRequest;
import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.dto.response.SavedFilterResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.SavedFilterService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/saved-filters")
@RequiredArgsConstructor
public class SavedFilterController {
    private final SavedFilterService filters;
    private final CurrentUser currentUser;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedFilterResponse create(
            @Valid @RequestBody SavedFilterRequest request
    ) {
        return filters.create(request, currentUser.get());
    }

    @GetMapping
    public List<SavedFilterResponse> list() {
        return filters.list(currentUser.get());
    }

    @GetMapping("/{id}")
    public SavedFilterResponse get(@PathVariable String id) {
        return filters.get(id, currentUser.get());
    }

    @PutMapping("/{id}")
    public SavedFilterResponse update(
            @PathVariable String id,
            @Valid @RequestBody SavedFilterRequest request
    ) {
        return filters.update(id, request, currentUser.get());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        filters.delete(id, currentUser.get());
    }

    @GetMapping("/{id}/issues")
    public List<IssueResponse> execute(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return filters.execute(id, page, size, currentUser.get());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Saved filter changed. Reload it and try again."));
    }
}