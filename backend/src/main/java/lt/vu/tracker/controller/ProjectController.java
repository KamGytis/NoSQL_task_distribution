package lt.vu.tracker.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.ChangeRoleRequest;
import lt.vu.tracker.dto.request.CreateProjectRequest;
import lt.vu.tracker.dto.request.UpdateProjectRequest;
import lt.vu.tracker.dto.response.ProjectResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;
    private final CurrentUser currentUser;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest req) {
        return projectService.create(req, currentUser.get());
    }

    @GetMapping
    public List<ProjectResponse> mine() {
        return projectService.listMine(currentUser.get());
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable String id) {
        return projectService.get(id, currentUser.get());
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable String id, @Valid @RequestBody UpdateProjectRequest req) {
        return projectService.update(id, req, currentUser.get());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        projectService.delete(id, currentUser.get());
    }

    @PutMapping("/{id}/members/{userId}")
    public ProjectResponse changeRole(@PathVariable String id, @PathVariable String userId,
                                      @Valid @RequestBody ChangeRoleRequest req) {
        return projectService.changeRole(id, userId, req.role(), currentUser.get());
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable String id, @PathVariable String userId) {
        projectService.removeMember(id, userId, currentUser.get());
    }
}