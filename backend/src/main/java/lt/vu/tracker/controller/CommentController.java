package lt.vu.tracker.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.CommentRequest;
import lt.vu.tracker.dto.response.CommentResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.CommentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;
    private final CurrentUser currentUser;

    @PostMapping("/issues/{key}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse add(@PathVariable String key, @Valid @RequestBody CommentRequest request) {
        return commentService.add(key, request, currentUser.get());
    }

    @GetMapping("/issues/{key}/comments")
    public List<CommentResponse> list(@PathVariable String key) {
        return commentService.list(key, currentUser.get());
    }

    @PutMapping("/comments/{id}")
    public CommentResponse update(@PathVariable String id, @Valid @RequestBody CommentRequest request) {
        return commentService.update(id, request, currentUser.get());
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        commentService.delete(id, currentUser.get());
    }
}