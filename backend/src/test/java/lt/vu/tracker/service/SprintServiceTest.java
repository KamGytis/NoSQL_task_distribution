package lt.vu.tracker.service;

import lt.vu.tracker.dto.response.IssueResponse;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.Sprint;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.SprintStatus;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.SprintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static lt.vu.tracker.TestData.assertStatus;
import static lt.vu.tracker.TestData.user;
import static lt.vu.tracker.model.enums.ProjectRole.VIEWER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SprintServiceTest {
    private final SprintRepository sprints = mock(SprintRepository.class);
    private final IssueRepository issues = mock(IssueRepository.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final SprintLock lock = mock(SprintLock.class);
    private final SprintService service = new SprintService(sprints, issues, permissions, lock);
    private final User current = user("u1", "sprint-test@test.lt");

    @BeforeEach
    void executeActionInsideMockLock() {
        when(lock.withLock(eq("p1"), any())).thenAnswer(invocation ->
                invocation.<Supplier<Object>>getArgument(1).get());
    }

    @Test
    void listsUnassignedIssuesForRequestedProject() {
        Issue issue = new Issue();
        issue.setKey("DEMO-1");
        when(issues.findByProjectIdAndSprintIdIsNullOrderByCreatedAtDesc("p1"))
                .thenReturn(List.of(issue));

        assertThat(service.backlog("p1", current)).extracting(IssueResponse::key).containsExactly("DEMO-1");
        verify(permissions).requireRole("p1", "u1", VIEWER);
    }

    @Test
    void rejectsAssignmentToAnotherProject() {
        Issue issue = new Issue();
        issue.setProjectId("p1");
        when(issues.findByKey("DEMO-1")).thenReturn(Optional.of(issue));
        Sprint other = sprint("p2", SprintStatus.PLANNED);

        assertStatus(() -> service.assign("DEMO-1", other.getId(), current), 404);
        verify(issues, never()).save(any(Issue.class));
    }

    @Test
    void rejectsStartingAnotherSprintWhileOneIsActive() {
        Sprint planned = sprint("p1", SprintStatus.PLANNED);
        when(sprints.existsByProjectIdAndStatus("p1", SprintStatus.ACTIVE)).thenReturn(true);

        assertStatus(() -> service.start(planned.getId(), current), 409);
        verify(sprints, never()).save(any(Sprint.class));
    }

    @Test
    void rejectsCompletingAPlannedSprint() {
        Sprint planned = sprint("p1", SprintStatus.PLANNED);

        assertStatus(() -> service.complete(planned.getId(), current), 409);
        verify(sprints, never()).save(any(Sprint.class));
    }

    @Test
    void completesAnActiveSprint() {
        Sprint active = sprint("p1", SprintStatus.ACTIVE);
        when(sprints.save(active)).thenReturn(active);

        assertThat(service.complete(active.getId(), current).status()).isEqualTo(SprintStatus.COMPLETED);
        assertThat(active.getCompletedAt()).isNotNull();
    }

    private Sprint sprint(String projectId, SprintStatus status) {
        Sprint sprint = new Sprint();
        sprint.setId("s1");
        sprint.setProjectId(projectId);
        sprint.setStatus(status);
        when(sprints.findById(sprint.getId())).thenReturn(Optional.of(sprint));
        return sprint;
    }
}
