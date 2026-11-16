package lt.vu.tracker.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.InviteRequest;
import lt.vu.tracker.dto.response.InvitationResponse;
import lt.vu.tracker.dto.response.ProjectResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.InvitationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InvitationController {
    private final InvitationService invitationService;
    private final CurrentUser currentUser;

    @PostMapping("/projects/{projectId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@PathVariable String projectId, @Valid @RequestBody InviteRequest req) {
        return invitationService.invite(projectId, req, currentUser.get());
    }

    @GetMapping("/invitations")
    public List<InvitationResponse> mine() {
        return invitationService.listMine(currentUser.get());
    }

    @PostMapping("/invitations/{id}/accept")
    public ProjectResponse accept(@PathVariable String id) {
        return invitationService.accept(id, currentUser.get());
    }

    @PostMapping("/invitations/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable String id) {
        invitationService.decline(id, currentUser.get());
    }
}