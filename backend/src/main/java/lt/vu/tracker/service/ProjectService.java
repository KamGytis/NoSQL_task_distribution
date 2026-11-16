package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.CreateProjectRequest;
import lt.vu.tracker.dto.request.UpdateProjectRequest;
import lt.vu.tracker.dto.response.MemberResponse;
import lt.vu.tracker.dto.response.ProjectResponse;
import lt.vu.tracker.model.Project;
import lt.vu.tracker.model.ProjectMember;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.ProjectRole;
import lt.vu.tracker.repository.InvitationRepository;
import lt.vu.tracker.repository.ProjectRepository;
import lt.vu.tracker.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    public ProjectResponse create(CreateProjectRequest req, User current) {
        if (projectRepository.existsByKey(req.key())) {
            throw conflict("Project key already in use");
        }
        Project project = new Project();
        project.setKey(req.key());
        project.setName(req.name().trim());
        project.setDescription(req.description());
        project.setLeadId(current.getId());
        project.getMembers().add(new ProjectMember(current.getId(), ProjectRole.ADMIN));
        project.setCreatedAt(Instant.now());
        try {
            project = projectRepository.save(project);
        } catch (DuplicateKeyException e) {
            throw conflict("Project key already in use");
        }
        return toResponse(project, ProjectRole.ADMIN);
    }

    public List<ProjectResponse> listMine(User current) {
        return projectRepository.findByMembersUserId(current.getId()).stream()
                .map(p -> toResponse(p, permissionService.roleOf(p, current.getId()).orElse(null)))
                .toList();
    }

    public ProjectResponse get(String projectId, User current) {
        Project project = permissionService.requireRole(projectId, current.getId(), ProjectRole.VIEWER);
        return toResponse(project, myRole(project, current));
    }

    public ProjectResponse update(String projectId, UpdateProjectRequest req, User current) {
        Project project = permissionService.requireRole(projectId, current.getId(), ProjectRole.ADMIN);
        project.setName(req.name().trim());
        project.setDescription(req.description());
        return toResponse(projectRepository.save(project), ProjectRole.ADMIN);
    }

    public void delete(String projectId, User current) {
        Project project = permissionService.requireRole(projectId, current.getId(), ProjectRole.ADMIN);
        invitationRepository.deleteByProjectId(project.getId());
        projectRepository.delete(project);
    }

    public ProjectResponse changeRole(String projectId, String targetUserId, ProjectRole newRole, User current) {
        Project project = permissionService.requireRole(projectId, current.getId(), ProjectRole.ADMIN);
        ProjectMember target = findMember(project, targetUserId);
        if (target.getRole() == ProjectRole.ADMIN && newRole != ProjectRole.ADMIN && adminCount(project) == 1) {
            throw conflict("A project must keep at least one admin");
        }
        target.setRole(newRole);
        projectRepository.save(project);
        return toResponse(project, myRole(project, current));
    }

    // Admins can remove anyone; any member can remove themselves (leave).
    public void removeMember(String projectId, String targetUserId, User current) {
        boolean leaving = current.getId().equals(targetUserId);
        Project project = permissionService.requireRole(projectId, current.getId(),
                leaving ? ProjectRole.VIEWER : ProjectRole.ADMIN);
        ProjectMember target = findMember(project, targetUserId);
        if (target.getRole() == ProjectRole.ADMIN && adminCount(project) == 1) {
            throw conflict("The last admin cannot leave or be removed");
        }
        project.getMembers().removeIf(m -> m.getUserId().equals(targetUserId));
        projectRepository.save(project);
    }

    public ProjectResponse toResponse(Project project, ProjectRole myRole) {
        List<String> ids = project.getMembers().stream().map(ProjectMember::getUserId).toList();
        Map<String, User> users = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<MemberResponse> members = project.getMembers().stream().map(m -> {
            User u = users.get(m.getUserId());
            return new MemberResponse(m.getUserId(),
                    u == null ? null : u.getFirstName(),
                    u == null ? null : u.getLastName(),
                    u == null ? null : u.getEmail(),
                    m.getRole());
        }).toList();
        return new ProjectResponse(project.getId(), project.getKey(), project.getName(),
                project.getDescription(), project.getLeadId(), members, myRole, project.getCreatedAt());
    }

    private ProjectRole myRole(Project project, User current) {
        return permissionService.roleOf(project, current.getId()).orElse(null);
    }

    private ProjectMember findMember(Project project, String userId) {
        return project.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    }

    private long adminCount(Project project) {
        return project.getMembers().stream().filter(m -> m.getRole() == ProjectRole.ADMIN).count();
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}