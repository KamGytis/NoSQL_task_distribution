package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.InviteRequest;
import lt.vu.tracker.dto.response.InvitationResponse;
import lt.vu.tracker.dto.response.ProjectResponse;
import lt.vu.tracker.model.Invitation;
import lt.vu.tracker.model.Project;
import lt.vu.tracker.model.ProjectMember;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.InvitationStatus;
import lt.vu.tracker.model.enums.ProjectRole;
import lt.vu.tracker.repository.InvitationRepository;
import lt.vu.tracker.repository.ProjectRepository;
import lt.vu.tracker.repository.UserRepository;
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
public class InvitationService {
    private final InvitationRepository invitationRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final ProjectService projectService;

    public InvitationResponse invite(String projectId, InviteRequest req, User current) {
        Project project = permissionService.requireRole(projectId, current.getId(), ProjectRole.ADMIN);
        User invited = userRepository.findByEmail(req.email().trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No user with that email"));
        if (permissionService.roleOf(project, invited.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member");
        }
        if (invitationRepository.existsByProjectIdAndInvitedUserIdAndStatus(
                projectId, invited.getId(), InvitationStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already has a pending invitation");
        }
        Invitation inv = new Invitation();
        inv.setProjectId(project.getId());
        inv.setProjectName(project.getName());
        inv.setInvitedUserId(invited.getId());
        inv.setInvitedBy(current.getId());
        inv.setRole(req.role());
        inv.setStatus(InvitationStatus.PENDING);
        inv.setCreatedAt(Instant.now());
        return toResponse(invitationRepository.save(inv), Map.of(current.getId(), current));
    }

    public List<InvitationResponse> listMine(User current) {
        List<Invitation> invitations =
                invitationRepository.findByInvitedUserIdAndStatus(current.getId(), InvitationStatus.PENDING);
        List<String> inviterIds = invitations.stream().map(Invitation::getInvitedBy).distinct().toList();
        Map<String, User> inviters = userRepository.findAllById(inviterIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return invitations.stream().map(i -> toResponse(i, inviters)).toList();
    }

    public ProjectResponse accept(String invitationId, User current) {
        Invitation inv = pendingInvitationFor(invitationId, current);
        Project project = projectRepository.findById(inv.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project no longer exists"));
        if (permissionService.roleOf(project, current.getId()).isEmpty()) {
            project.getMembers().add(new ProjectMember(current.getId(), inv.getRole()));
            projectRepository.save(project);
        }
        inv.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(inv);
        return projectService.toResponse(project, permissionService.roleOf(project, current.getId()).orElse(null));
    }

    public void decline(String invitationId, User current) {
        Invitation inv = pendingInvitationFor(invitationId, current);
        inv.setStatus(InvitationStatus.DECLINED);
        invitationRepository.save(inv);
    }

    private Invitation pendingInvitationFor(String invitationId, User current) {
        Invitation inv = invitationRepository.findById(invitationId)
                .filter(i -> i.getInvitedUserId().equals(current.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
        if (inv.getStatus() != InvitationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation already answered");
        }
        return inv;
    }

    private InvitationResponse toResponse(Invitation i, Map<String, User> inviters) {
        User by = inviters.get(i.getInvitedBy());
        String name = by == null ? null : by.getFirstName() + " " + by.getLastName();
        return new InvitationResponse(i.getId(), i.getProjectId(), i.getProjectName(),
                i.getRole(), i.getStatus(), name, i.getCreatedAt());
    }
}