package lt.vu.tracker.controller;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.response.BoardResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.BoardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BoardController {
    private final BoardService boardService;
    private final CurrentUser currentUser;

    @GetMapping("/projects/{projectId}/board")
    public BoardResponse board(@PathVariable String projectId) {
        return boardService.get(projectId, currentUser.get());
    }
}