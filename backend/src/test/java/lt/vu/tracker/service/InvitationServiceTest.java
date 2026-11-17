package lt.vu.tracker.service;

import lt.vu.tracker.dto.request.InviteRequest;
import lt.vu.tracker.dto.response.InvitationResponse;
import lt.vu.tracker.dto.response.ProjectResponse;
import lt.vu.tracker.model.Invitation;
import lt.vu.tracker.model.Project;
import lt.vu.tracker.model.User;
import lt.vu.tracker.repository.InvitationRepository;
import lt.vu.tracker.repository.ProjectRepository;
import lt.vu.tracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static lt.vu.tracker.TestData.*;
import static lt.vu.tracker.model.enums.InvitationStatus.*;
import static lt.vu.tracker.model.enums.ProjectRole.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvitationServiceTest {
    @Mock InvitationRepository invitationRepository;
    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    InvitationService service;

    final User alice = user("u1", "alice@test.lt");
    final User bob = user("u2", "bob@test.lt");

    @BeforeEach
    void setUp() {
        PermissionService permissions = new PermissionService(projectRepository);
        ProjectService projectService =
                new ProjectService(projectRepository, invitationRepository, userRepository, permissions);
        service = new InvitationService(invitationRepository, projectRepository, userRepository,
                permissions, projectService);
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> i.getArgument(0));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Project stub(Project p) {
        when(projectRepository.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private Invitation pendingInvitationForBob() {
        Invitation inv = new Invitation();
        inv.setId("i1");
        inv.setProjectId("p1");
        inv.setProjectName("Demo");
        inv.setInvitedUserId("u2");
        inv.setInvitedBy("u1");
        inv.setRole(MEMBER);
        inv.setStatus(PENDING);
        when(invitationRepository.findById("i1")).thenReturn(Optional.of(inv));
        return inv;
    }

    //invite

    @Test
    void inviteCreatesAPendingInvitation() {
        stub(project("p1", member("u1", ADMIN)));
        when(userRepository.findByEmail("bob@test.lt")).thenReturn(Optional.of(bob));

        InvitationResponse res = service.invite("p1", new InviteRequest("  Bob@Test.lt ", MEMBER), alice);

        ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository).save(captor.capture());
        Invitation saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo("p1");
        assertThat(saved.getProjectName()).isEqualTo("Demo");
        assertThat(saved.getInvitedUserId()).isEqualTo("u2");
        assertThat(saved.getInvitedBy()).isEqualTo("u1");
        assertThat(saved.getRole()).isEqualTo(MEMBER);
        assertThat(saved.getStatus()).isEqualTo(PENDING);
        assertThat(res.status()).isEqualTo(PENDING);
    }

    @Test
    void inviteRequiresAdmin() {
        stub(project("p1", member("u1", ADMIN), member("u2", MEMBER)));
        assertStatus(() -> service.invite("p1", new InviteRequest("alice@test.lt", VIEWER), bob), 403);
    }

    @Test
    void inviteUnknownEmailIs404() {
        stub(project("p1", member("u1", ADMIN)));
        when(userRepository.findByEmail("ghost@test.lt")).thenReturn(Optional.empty());
        assertStatus(() -> service.invite("p1", new InviteRequest("ghost@test.lt", MEMBER), alice), 404);
    }

    @Test
    void inviteExistingMemberIs409() {
        stub(project("p1", member("u1", ADMIN), member("u2", MEMBER)));
        when(userRepository.findByEmail("bob@test.lt")).thenReturn(Optional.of(bob));
        assertStatus(() -> service.invite("p1", new InviteRequest("bob@test.lt", MEMBER), alice), 409);
    }

    @Test
    void inviteWithAPendingInvitationIs409() {
        stub(project("p1", member("u1", ADMIN)));
        when(userRepository.findByEmail("bob@test.lt")).thenReturn(Optional.of(bob));
        when(invitationRepository.existsByProjectIdAndInvitedUserIdAndStatus("p1", "u2", PENDING))
                .thenReturn(true);
        assertStatus(() -> service.invite("p1", new InviteRequest("bob@test.lt", MEMBER), alice), 409);
        verify(invitationRepository, never()).save(any());
    }

    //accept / decline

    @Test
    void acceptAddsTheMemberAndMarksTheInvitationAccepted() {
        Invitation inv = pendingInvitationForBob();
        Project p = stub(project("p1", member("u1", ADMIN)));

        ProjectResponse res = service.accept("i1", bob);

        assertThat(p.getMembers()).extracting("userId").containsExactly("u1", "u2");
        assertThat(p.getMembers().get(1).getRole()).isEqualTo(MEMBER);
        assertThat(inv.getStatus()).isEqualTo(ACCEPTED);
        assertThat(res.myRole()).isEqualTo(MEMBER);
    }

    @Test
    void cannotAcceptSomeoneElsesInvitation() {
        pendingInvitationForBob();
        stub(project("p1", member("u1", ADMIN)));
        assertStatus(() -> service.accept("i1", alice), 404);
    }

    @Test
    void cannotAnswerAnInvitationTwice() {
        Invitation inv = pendingInvitationForBob();
        inv.setStatus(DECLINED);
        assertStatus(() -> service.accept("i1", bob), 409);
    }

    @Test
    void acceptingAnInvitationToADeletedProjectIs404() {
        pendingInvitationForBob();
        when(projectRepository.findById("p1")).thenReturn(Optional.empty());
        assertStatus(() -> service.accept("i1", bob), 404);
    }

    @Test
    void declineMarksDeclinedAndAddsNobody() {
        Invitation inv = pendingInvitationForBob();
        service.decline("i1", bob);
        assertThat(inv.getStatus()).isEqualTo(DECLINED);
        verify(projectRepository, never()).save(any());
    }

    //list

    @Test
    void listMineReturnsPendingInvitationsWithTheInvitersName() {
        Invitation inv = pendingInvitationForBob();
        when(invitationRepository.findByInvitedUserIdAndStatus("u2", PENDING)).thenReturn(List.of(inv));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(alice));

        List<InvitationResponse> result = service.listMine(bob);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).projectName()).isEqualTo("Demo");
        assertThat(result.get(0).invitedByName()).isEqualTo("Test User");
    }
}