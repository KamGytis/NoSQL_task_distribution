package lt.vu.tracker.service;

import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.IssueStatus;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.ProjectRepository;
import lt.vu.tracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static lt.vu.tracker.TestData.*;
import static lt.vu.tracker.model.enums.ProjectRole.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IssueAssignmentServiceTest {
    private final IssueRepository issues = mock(IssueRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final IssueHistoryService history = mock(IssueHistoryService.class);
    private final IssueAssignmentService service = new IssueAssignmentService(
            issues, users, new PermissionService(projects), notifications, history);

    private final User alice = user("u1", "alice@test.lt");   // ADMIN
    private final User bob = user("u2", "bob@test.lt");       // MEMBER
    private final User carol = user("u3", "carol@test.lt");   // VIEWER
    private final User dave = user("u4", "dave@test.lt");     // not a member
    private final User erin = user("u5", "erin@test.lt");     // MEMBER
    private final List<User> everyone = List.of(alice, bob, carol, dave, erin);

    @BeforeEach
    void setUp() {
        when(projects.findById("p1")).thenReturn(Optional.of(project("p1",
                member("u1", ADMIN), member("u2", MEMBER), member("u3", VIEWER), member("u5", MEMBER))));
        when(issues.save(any(Issue.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findById(anyString())).thenAnswer(inv ->
                everyone.stream().filter(u -> u.getId().equals(inv.getArgument(0))).findFirst());
    }

    private Issue issue(String assigneeId) {
        Issue i = new Issue();
        i.setId("i1");
        i.setKey("DEMO-1");
        i.setProjectId("p1");
        i.setTitle("Title");
        i.setStatus(IssueStatus.TODO);
        i.setAssigneeId(assigneeId);
        when(issues.findByKey("DEMO-1")).thenReturn(Optional.of(i));
        return i;
    }

    //  assign

    @Test
    void assigningSavesRecordsAndNotifies() {
        Issue issue = issue(null);

        IssueResponse res = service.assign("DEMO-1", "u2", alice);

        assertThat(issue.getAssigneeId()).isEqualTo("u2");
        assertThat(res.assigneeId()).isEqualTo("u2");
        verify(issues).save(issue);
        verify(history).recordAssigned(issue, null, bob, alice);
        verify(notifications).notifyAssigned(bob, alice, issue);
    }

    @Test
    void reassigningRecordsTheOldAssignee() {
        Issue issue = issue("u5");

        service.assign("DEMO-1", "u2", alice);

        assertThat(issue.getAssigneeId()).isEqualTo("u2");
        verify(history).recordAssigned(issue, erin, bob, alice);
        verify(notifications).notifyAssigned(bob, alice, issue);
    }

    @Test
    void assigningToYourselfIsAllowedAndRecorded() {
        Issue issue = issue(null);

        service.assign("DEMO-1", "u2", bob);

        assertThat(issue.getAssigneeId()).isEqualTo("u2");
        verify(history).recordAssigned(issue, null, bob, bob);
        verify(notifications).notifyAssigned(bob, bob, issue);   // the notification service skips self-assignment
    }

    @Test
    void assigningTheSamePersonAgainChangesNothing() {
        issue("u2");

        IssueResponse res = service.assign("DEMO-1", "u2", alice);

        assertThat(res.assigneeId()).isEqualTo("u2");
        verify(issues, never()).save(any(Issue.class));
        verifyNoInteractions(history, notifications);
    }

    @Test
    void aViewerCannotBeAnAssignee() {
        Issue issue = issue(null);
        assertStatus(() -> service.assign("DEMO-1", "u3", alice), 400);
        assertThat(issue.getAssigneeId()).isNull();
        verify(issues, never()).save(any(Issue.class));
        verifyNoInteractions(history, notifications);
    }

    @Test
    void aNonMemberCannotBeAnAssignee() {
        issue(null);
        assertStatus(() -> service.assign("DEMO-1", "u4", alice), 400);
        verify(issues, never()).save(any(Issue.class));
    }

    @Test
    void aMemberWhoseAccountIsGoneIsRejected() {
        issue(null);
        doReturn(Optional.empty()).when(users).findById("u2");
        assertStatus(() -> service.assign("DEMO-1", "u2", alice), 400);
        verify(issues, never()).save(any(Issue.class));
    }

    @Test
    void viewersCannotAssign() {
        issue(null);
        assertStatus(() -> service.assign("DEMO-1", "u2", carol), 403);
        verify(issues, never()).save(any(Issue.class));
    }

    @Test
    void nonMembersCannotAssign() {
        issue(null);
        assertStatus(() -> service.assign("DEMO-1", "u2", dave), 404);
    }

    @Test
    void anUnknownIssueIs404() {
        when(issues.findByKey("DEMO-99")).thenReturn(Optional.empty());
        assertStatus(() -> service.assign("DEMO-99", "u2", alice), 404);
    }

    @Test
    void theIssueKeyIsCaseInsensitive() {
        issue(null);
        assertThat(service.assign("demo-1", "u2", alice).key()).isEqualTo("DEMO-1");
    }

    //  unassign

    @Test
    void unassigningClearsTheAssigneeAndNotifiesNobody() {
        Issue issue = issue("u2");

        IssueResponse res = service.unassign("DEMO-1", alice);

        assertThat(issue.getAssigneeId()).isNull();
        assertThat(res.assigneeId()).isNull();
        verify(issues).save(issue);
        verify(history).recordAssigned(issue, bob, null, alice);
        verifyNoInteractions(notifications);
    }

    @Test
    void unassigningAnUnassignedIssueChangesNothing() {
        issue(null);

        service.unassign("DEMO-1", alice);

        verify(issues, never()).save(any(Issue.class));
        verifyNoInteractions(history, notifications);
    }

    @Test
    void viewersCannotUnassign() {
        Issue issue = issue("u2");
        assertStatus(() -> service.unassign("DEMO-1", carol), 403);
        assertThat(issue.getAssigneeId()).isEqualTo("u2");
    }
}