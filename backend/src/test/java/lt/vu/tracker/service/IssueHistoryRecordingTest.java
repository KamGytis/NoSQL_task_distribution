package lt.vu.tracker.service;

import lt.vu.tracker.dto.request.IssueRequest;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.IssueStatus;
import lt.vu.tracker.repository.CommentRepository;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;

import static lt.vu.tracker.TestData.*;
import static lt.vu.tracker.model.enums.IssueStatus.*;
import static lt.vu.tracker.model.enums.ProjectRole.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IssueHistoryRecordingTest {
    private final IssueRepository issues = mock(IssueRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final IssueHistoryService history = mock(IssueHistoryService.class);
    private final IssueService service = new IssueService(
            issues, new PermissionService(projects), redis, mock(CommentRepository.class), history);

    private final User alice = user("u1", "alice@test.lt");   // ADMIN
    private final User bob = user("u2", "bob@test.lt");       // MEMBER
    private final User carol = user("u3", "carol@test.lt");   // VIEWER

    @BeforeEach
    void setUp() {
        when(projects.findById("p1")).thenReturn(Optional.of(
                project("p1", member("u1", ADMIN), member("u2", MEMBER), member("u3", VIEWER))));
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment("tracker:issue-counter:DEMO")).thenReturn(7L);
        when(issues.insert(any(Issue.class))).thenAnswer(i -> i.getArgument(0));
        when(issues.save(any(Issue.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Issue existing(IssueStatus status) {
        Issue i = new Issue();
        i.setId("i1");
        i.setKey("DEMO-1");
        i.setProjectId("p1");
        i.setTitle("Old title");
        i.setDescription("old desc");
        i.setStatus(status);
        i.setReporterId("u1");
        i.setCreatedAt(Instant.now());
        when(issues.findByKey("DEMO-1")).thenReturn(Optional.of(i));
        return i;
    }

    @Test
    void creatingAnIssueIsRecorded() {
        service.create("p1", new IssueRequest("  Fix login  ", "desc"), bob);

        ArgumentCaptor<Issue> captor = ArgumentCaptor.forClass(Issue.class);
        verify(history).recordCreated(captor.capture(), eq(bob));
        assertThat(captor.getValue().getKey()).isEqualTo("DEMO-7");
        assertThat(captor.getValue().getTitle()).isEqualTo("Fix login");
    }

    @Test
    void updatingPassesTheOldValuesToTheHistory() {
        existing(TODO);

        service.update("DEMO-1", new IssueRequest("New title", "new desc"), bob);

        verify(history).recordUpdated(any(Issue.class), eq("Old title"), eq("old desc"), eq(bob));
    }

    @Test
    void aTransitionIsRecordedWithBothStatuses() {
        existing(TODO);

        service.transition("DEMO-1", IN_PROGRESS, bob);

        verify(history).recordTransition(any(Issue.class), eq(TODO), eq(IN_PROGRESS), eq(bob));
    }

    @Test
    void movingToTheCurrentStatusRecordsNothing() {
        existing(IN_PROGRESS);
        service.transition("DEMO-1", IN_PROGRESS, bob);
        verifyNoInteractions(history);
    }

    @Test
    void aRejectedTransitionRecordsNothing() {
        existing(TODO);
        assertStatus(() -> service.transition("DEMO-1", DONE, bob), 400);
        verifyNoInteractions(history);
    }

    @Test
    void deletingIsRecorded() {
        Issue issue = existing(TODO);
        service.delete("DEMO-1", alice);
        verify(history).recordDeleted(issue, alice);
    }

    @Test
    void forbiddenChangesRecordNothing() {
        existing(TODO);
        assertStatus(() -> service.update("DEMO-1", new IssueRequest("X", null), carol), 403);
        assertStatus(() -> service.transition("DEMO-1", IN_PROGRESS, carol), 403);
        assertStatus(() -> service.delete("DEMO-1", bob), 403);
        verifyNoInteractions(history);
    }
}