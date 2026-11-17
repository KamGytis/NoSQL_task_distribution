package lt.vu.tracker.service;

import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.User;
import lt.vu.tracker.repository.CommentRepository;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static lt.vu.tracker.TestData.*;
import static lt.vu.tracker.model.enums.ProjectRole.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IssueDeleteTest {
    private final IssueRepository issues = mock(IssueRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final IssueService service = new IssueService(
            issues, new PermissionService(projects), mock(StringRedisTemplate.class), comments);

    private final User alice = user("u1", "alice@test.lt");   // ADMIN
    private final User bob = user("u2", "bob@test.lt");       // MEMBER
    private Issue issue;

    @BeforeEach
    void setUp() {
        when(projects.findById("p1")).thenReturn(Optional.of(
                project("p1", member("u1", ADMIN), member("u2", MEMBER))));
        issue = new Issue();
        issue.setId("i1");
        issue.setKey("DEMO-1");
        issue.setProjectId("p1");
        when(issues.findByKey("DEMO-1")).thenReturn(Optional.of(issue));
    }

    @Test
    void deletingAnIssueAlsoDeletesItsComments() {
        service.delete("DEMO-1", alice);
        verify(issues).delete(issue);
        verify(comments).deleteByIssueId("i1");
    }

    @Test
    void membersCannotDeleteIssuesAndNothingIsRemoved() {
        assertStatus(() -> service.delete("DEMO-1", bob), 403);
        verify(issues, never()).delete(any(Issue.class));
        verify(comments, never()).deleteByIssueId(any());
    }
}