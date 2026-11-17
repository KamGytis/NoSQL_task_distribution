package lt.vu.tracker.service;

import lt.vu.tracker.dto.response.BoardColumnResponse;
import lt.vu.tracker.dto.response.BoardResponse;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.IssueStatus;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static lt.vu.tracker.TestData.*;
import static lt.vu.tracker.model.enums.IssueStatus.*;
import static lt.vu.tracker.model.enums.ProjectRole.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoardServiceTest {
    private final IssueRepository issues = mock(IssueRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final BoardService service = new BoardService(issues, new PermissionService(projects));

    private final User alice = user("u1", "alice@test.lt");   // ADMIN
    private final User carol = user("u3", "carol@test.lt");   // VIEWER

    @BeforeEach
    void setUp() {
        when(projects.findById("p1")).thenReturn(Optional.of(
                project("p1", member("u1", ADMIN), member("u3", VIEWER))));
    }

    private Issue issue(String key, IssueStatus status) {
        Issue i = new Issue();
        i.setId(key);
        i.setKey(key);
        i.setProjectId("p1");
        i.setTitle("Title " + key);
        i.setStatus(status);
        i.setReporterId("u1");
        i.setCreatedAt(Instant.now());
        return i;
    }

    private BoardColumnResponse column(BoardResponse board, IssueStatus status) {
        return board.columns().stream().filter(c -> c.status() == status).findFirst().orElseThrow();
    }

    @Test
    void groupsIssuesByStatusInWorkflowOrder() {
        when(issues.findByProjectIdOrderByCreatedAtDesc("p1")).thenReturn(List.of(
                issue("DEMO-4", TODO), issue("DEMO-3", DONE), issue("DEMO-2", IN_PROGRESS), issue("DEMO-1", TODO)));

        BoardResponse board = service.get("p1", alice);

        assertThat(board.columns()).extracting(BoardColumnResponse::status)
                .containsExactly(TODO, IN_PROGRESS, DONE);
        assertThat(column(board, TODO).issues()).extracting("key").containsExactly("DEMO-4", "DEMO-1");
        assertThat(column(board, IN_PROGRESS).issues()).extracting("key").containsExactly("DEMO-2");
        assertThat(column(board, DONE).issues()).extracting("key").containsExactly("DEMO-3");
        assertThat(column(board, TODO).count()).isEqualTo(2);
    }

    @Test
    void everyColumnShowsWhereItsIssuesCanMove() {
        when(issues.findByProjectIdOrderByCreatedAtDesc("p1")).thenReturn(List.of());

        BoardResponse board = service.get("p1", alice);

        assertThat(column(board, TODO).movesTo()).containsExactly(IN_PROGRESS);
        assertThat(column(board, IN_PROGRESS).movesTo()).containsExactly(TODO, DONE);
        assertThat(column(board, DONE).movesTo()).containsExactly(IN_PROGRESS);
    }

    @Test
    void emptyColumnsStillAppear() {
        when(issues.findByProjectIdOrderByCreatedAtDesc("p1")).thenReturn(List.of());

        BoardResponse board = service.get("p1", alice);

        assertThat(board.columns()).hasSize(3);
        assertThat(board.columns()).allSatisfy(c -> {
            assertThat(c.count()).isZero();
            assertThat(c.issues()).isEmpty();
        });
    }

    @Test
    void reportsTheProjectAndTheCallersRole() {
        when(issues.findByProjectIdOrderByCreatedAtDesc("p1")).thenReturn(List.of());

        BoardResponse board = service.get("p1", carol);

        assertThat(board.projectId()).isEqualTo("p1");
        assertThat(board.projectKey()).isEqualTo("DEMO");
        assertThat(board.myRole()).isEqualTo(VIEWER);
    }

    @Test
    void nonMembersCannotSeeTheBoard() {
        assertStatus(() -> service.get("p1", user("u9", "x@test.lt")), 404);
    }

    @Test
    void unknownProjectIs404() {
        when(projects.findById("nope")).thenReturn(Optional.empty());
        assertStatus(() -> service.get("nope", alice), 404);
    }
}