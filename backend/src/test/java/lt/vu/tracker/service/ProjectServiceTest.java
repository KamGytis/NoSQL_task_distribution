package lt.vu.tracker.service;

import lt.vu.tracker.dto.request.CreateProjectRequest;
import lt.vu.tracker.dto.request.UpdateProjectRequest;
import lt.vu.tracker.dto.response.ProjectResponse;
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

import java.util.Optional;

import static lt.vu.tracker.TestData.*;
import static lt.vu.tracker.model.enums.ProjectRole.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceTest {
    @Mock ProjectRepository projectRepository;
    @Mock InvitationRepository invitationRepository;
    @Mock UserRepository userRepository;
    ProjectService service;

    final User alice = user("u1", "alice@test.lt");
    final User bob = user("u2", "bob@test.lt");

    @BeforeEach
    void setUp() {
        PermissionService permissions = new PermissionService(projectRepository);
        service = new ProjectService(projectRepository, invitationRepository, userRepository, permissions);
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Project stub(Project p) {
        when(projectRepository.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    //create

    @Test
    void createMakesTheCreatorAnAdmin() {
        ProjectResponse res = service.create(new CreateProjectRequest("DEMO", "  Demo  ", "desc"), alice);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Demo");
        assertThat(saved.getLeadId()).isEqualTo("u1");
        assertThat(saved.getMembers()).hasSize(1);
        assertThat(saved.getMembers().get(0).getUserId()).isEqualTo("u1");
        assertThat(saved.getMembers().get(0).getRole()).isEqualTo(ADMIN);
        assertThat(res.myRole()).isEqualTo(ADMIN);
    }

    @Test
    void createRejectsDuplicateKey() {
        when(projectRepository.existsByKey("DEMO")).thenReturn(true);
        assertStatus(() -> service.create(new CreateProjectRequest("DEMO", "Demo", null), alice), 409);
        verify(projectRepository, never()).save(any());
    }

    //read / update / delete

    @Test
    void nonMembersCannotSeeAProject() {
        stub(project("p1", member("u1", ADMIN)));
        assertStatus(() -> service.get("p1", bob), 404);
    }

    @Test
    void viewersCanReadButNotUpdate() {
        stub(project("p1", member("u1", ADMIN), member("u2", VIEWER)));
        assertThat(service.get("p1", bob).myRole()).isEqualTo(VIEWER);
        assertStatus(() -> service.update("p1", new UpdateProjectRequest("New", "d"), bob), 403);
    }

    @Test
    void adminCanUpdate() {
        Project p = stub(project("p1", member("u1", ADMIN)));
        service.update("p1", new UpdateProjectRequest("  Renamed ", "d"), alice);
        assertThat(p.getName()).isEqualTo("Renamed");
    }

    @Test
    void deleteRemovesProjectAndItsInvitations() {
        Project p = stub(project("p1", member("u1", ADMIN)));
        service.delete("p1", alice);
        verify(invitationRepository).deleteByProjectId("p1");
        verify(projectRepository).delete(p);
    }

    @Test
    void membersCannotDelete() {
        stub(project("p1", member("u1", ADMIN), member("u2", MEMBER)));
        assertStatus(() -> service.delete("p1", bob), 403);
        verify(projectRepository, never()).delete(any(Project.class));
    }

    //roles

    @Test
    void lastAdminCannotBeDemoted() {
        stub(project("p1", member("u1", ADMIN), member("u2", MEMBER)));
        assertStatus(() -> service.changeRole("p1", "u1", MEMBER, alice), 409);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void anAdminCanBeDemotedWhenAnotherAdminExists() {
        Project p = stub(project("p1", member("u1", ADMIN), member("u2", ADMIN)));
        service.changeRole("p1", "u1", MEMBER, alice);
        assertThat(p.getMembers().get(0).getRole()).isEqualTo(MEMBER);
        verify(projectRepository).save(p);
    }

    @Test
    void adminCanPromoteAMember() {
        Project p = stub(project("p1", member("u1", ADMIN), member("u2", MEMBER)));
        service.changeRole("p1", "u2", ADMIN, alice);
        assertThat(p.getMembers().get(1).getRole()).isEqualTo(ADMIN);
    }

    @Test
    void membersCannotChangeRoles() {
        stub(project("p1", member("u1", ADMIN), member("u2", MEMBER)));
        assertStatus(() -> service.changeRole("p1", "u2", ADMIN, bob), 403);
    }

    @Test
    void changingTheRoleOfAnUnknownMemberIs404() {
        stub(project("p1", member("u1", ADMIN)));
        assertStatus(() -> service.changeRole("p1", "u9", MEMBER, alice), 404);
    }

    //removing members

    @Test
    void adminCanRemoveAMember() {
        Project p = stub(project("p1", member("u1", ADMIN), member("u2", MEMBER)));
        service.removeMember("p1", "u2", alice);
        assertThat(p.getMembers()).extracting("userId").containsExactly("u1");
    }

    @Test
    void aMemberCanLeaveTheProject() {
        Project p = stub(project("p1", member("u1", ADMIN), member("u2", MEMBER)));
        service.removeMember("p1", "u2", bob);
        assertThat(p.getMembers()).extracting("userId").containsExactly("u1");
    }

    @Test
    void aMemberCannotRemoveSomeoneElse() {
        stub(project("p1", member("u1", ADMIN), member("u2", MEMBER)));
        assertStatus(() -> service.removeMember("p1", "u1", bob), 403);
    }

    @Test
    void theLastAdminCannotLeave() {
        stub(project("p1", member("u1", ADMIN), member("u2", MEMBER)));
        assertStatus(() -> service.removeMember("p1", "u1", alice), 409);
    }
}