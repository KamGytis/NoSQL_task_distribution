package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.model.Project;
import lt.vu.tracker.model.ProjectMember;
import lt.vu.tracker.model.enums.ProjectRole;
import lt.vu.tracker.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final ProjectRepository projectRepository;

    // Returns the project if the user is valid. 404 for non-members, 403 for too low a role.
    public Project requireRole(String projectId, String userId, ProjectRole minimum) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        ProjectRole role = roleOf(project, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (!role.atLeast(minimum)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires role " + minimum);
        }
        return project;
    }

    public Optional<ProjectRole> roleOf(Project project, String userId) {
        return project.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId))
                .map(ProjectMember::getRole)
                .findFirst();
    }
}