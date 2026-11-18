package lt.vu.tracker.service;

import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.IssueStatus;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.ProjectRepository;
import lt.vu.tracker.repository.WorklogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Optional;

import static lt.vu.tracker.TestData.*;
import static lt.vu.tracker.model.enums.IssueStatus.*;
import static lt.vu.tracker.model.enums.ProjectRole.*;
import lt.vu.tracker.repository.CommentRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IssueTransitionTest {
    private final IssueRepository issues = mock(IssueRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final IssueService service = new IssueService(
            issues, new PermissionService(projects), redis, mock(CommentRepository.class),
            mock(IssueHistoryService.class), mock(WorklogRepository.class),
            mock(ProjectReadCache.class)
    );
    private final User alice = user("u1", "alice@test.lt");   // ADMIN
    private final User bob = user("u2", "bob@test.lt");       // MEMBER
    private final User carol = user("u3", "carol@test.lt");   // VIEWER

    @BeforeEach
    void setUp() {
        when(projects.findById("p1")).thenReturn(Optional.of(
                project("p1", member("u1", ADMIN), member("u2", MEMBER), member("u3", VIEWER))));
        when(issues.save(any(Issue.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Issue issueIn(IssueStatus status) {
        Issue issue = new Issue();
        issue.setId("i1");
        issue.setKey("DEMO-1");
        issue.setProjectId("p1");
        issue.setTitle("Title");
        issue.setStatus(status);
        issue.setReporterId("u1");
        issue.setCreatedAt(Instant.now());
        when(issues.findByKey("DEMO-1")).thenReturn(Optional.of(issue));
        return issue;
    }

    @Test
    void membersCanStartAnIssue() {
        Issue issue = issueIn(TODO);

        IssueResponse res = service.transition("DEMO-1", IN_PROGRESS, bob);

        assertThat(issue.getStatus()).isEqualTo(IN_PROGRESS);
        assertThat(res.status()).isEqualTo(IN_PROGRESS);
        verify(issues).save(issue);
    }

    @Test
    void anIssueCanTravelTheWholeWorkflow() {
        Issue issue = issueIn(TODO);
        service.transition("DEMO-1", IN_PROGRESS, bob);
        service.transition("DEMO-1", DONE, bob);
        assertThat(issue.getStatus()).isEqualTo(DONE);
    }

    @Test
    void aDoneIssueCanBeReopened() {
        Issue issue = issueIn(DONE);
        service.transition("DEMO-1", IN_PROGRESS, alice);
        assertThat(issue.getStatus()).isEqualTo(IN_PROGRESS);
    }

    @Test
    void skippingAStepIsRejectedAndNothingIsSaved() {
        Issue issue = issueIn(TODO);

        assertStatus(() -> service.transition("DEMO-1", DONE, bob), 400);

        assertThat(issue.getStatus()).isEqualTo(TODO);
        verify(issues, never()).save(any());
    }

    @Test
    void aDoneIssueCannotJumpBackToTodo() {
        issueIn(DONE);
        assertStatus(() -> service.transition("DEMO-1", TODO, bob), 400);
    }

    @Test
    void movingToTheCurrentStatusIsAHarmlessNoOp() {
        issueIn(IN_PROGRESS);

        IssueResponse res = service.transition("DEMO-1", IN_PROGRESS, bob);

        assertThat(res.status()).isEqualTo(IN_PROGRESS);
        verify(issues, never()).save(any());
    }

    @Test
    void viewersCannotTransition() {
        Issue issue = issueIn(TODO);
        assertStatus(() -> service.transition("DEMO-1", IN_PROGRESS, carol), 403);
        assertThat(issue.getStatus()).isEqualTo(TODO);
    }

    @Test
    void nonMembersCannotTransition() {
        issueIn(TODO);
        assertStatus(() -> service.transition("DEMO-1", IN_PROGRESS, user("u9", "x@test.lt")), 404);
    }

    @Test
    void unknownIssueIs404() {
        when(issues.findByKey("DEMO-99")).thenReturn(Optional.empty());
        assertStatus(() -> service.transition("DEMO-99", IN_PROGRESS, bob), 404);
    }
}