package lt.vu.tracker.service;

import lt.vu.tracker.dto.request.WorklogRequest;
import lt.vu.tracker.dto.response.WorklogListResponse;
import lt.vu.tracker.dto.response.WorklogResponse;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.Worklog;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.ProjectRepository;
import lt.vu.tracker.repository.UserRepository;
import lt.vu.tracker.repository.WorklogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static lt.vu.tracker.TestData.*;
import static lt.vu.tracker.model.enums.ProjectRole.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WorklogServiceTest {
    private final WorklogRepository worklogs = mock(WorklogRepository.class);
    private final IssueRepository issues = mock(IssueRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final WorklogService service =
            new WorklogService(worklogs, issues, users, new PermissionService(projects));

    private final User alice = user("u1", "alice@test.lt");   // ADMIN
    private final User bob = user("u2", "bob@test.lt");       // MEMBER
    private final User carol = user("u3", "carol@test.lt");   // VIEWER
    private final User dave = user("u4", "dave@test.lt");     // not a member
    private final List<User> everyone = List.of(alice, bob, carol, dave);

    @BeforeEach
    void setUp() {
        when(projects.findById("p1")).thenReturn(Optional.of(
                project("p1", member("u1", ADMIN), member("u2", MEMBER), member("u3", VIEWER))));
        when(issues.findByKey("DEMO-1")).thenReturn(Optional.of(issue()));
        when(worklogs.save(any(Worklog.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findAllById(anyIterable())).thenAnswer(inv -> {
            Iterable<String> given = inv.getArgument(0);
            Set<String> ids = new HashSet<>();
            given.forEach(ids::add);
            return everyone.stream().filter(u -> ids.contains(u.getId())).toList();
        });
    }

    private Issue issue() {
        Issue i = new Issue();
        i.setId("i1");
        i.setKey("DEMO-1");
        i.setProjectId("p1");
        i.setTitle("Title");
        return i;
    }

    private Worklog worklogBy(String id, String userId, int minutes) {
        Worklog w = new Worklog();
        w.setId(id);
        w.setIssueId("i1");
        w.setIssueKey("DEMO-1");
        w.setProjectId("p1");
        w.setUserId(userId);
        w.setMinutes(minutes);
        w.setStartedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        w.setNote("original");
        w.setCreatedAt(Instant.now());
        when(worklogs.findById(id)).thenReturn(Optional.of(w));
        return w;
    }

    private Worklog saved() {
        ArgumentCaptor<Worklog> captor = ArgumentCaptor.forClass(Worklog.class);
        verify(worklogs).save(captor.capture());
        return captor.getValue();
    }

    //  add

    @Test
    void membersCanLogTimeAndTheStartDefaultsToNow() {
        Instant before = Instant.now();

        WorklogResponse res = service.add("DEMO-1", new WorklogRequest(90, null, "  Wrote the endpoint  "), bob);

        Worklog w = saved();
        assertThat(w.getMinutes()).isEqualTo(90);
        assertThat(w.getUserId()).isEqualTo("u2");
        assertThat(w.getIssueId()).isEqualTo("i1");
        assertThat(w.getIssueKey()).isEqualTo("DEMO-1");
        assertThat(w.getProjectId()).isEqualTo("p1");
        assertThat(w.getNote()).isEqualTo("Wrote the endpoint");
        assertThat(w.getStartedAt()).isBetween(before, Instant.now());
        assertThat(w.getCreatedAt()).isNotNull();
        assertThat(w.getUpdatedAt()).isNull();
        assertThat(res.timeSpent()).isEqualTo("1h 30m");
        assertThat(res.author().email()).isEqualTo("bob@test.lt");
        assertThat(res.canEdit()).isTrue();
        assertThat(res.canDelete()).isTrue();
    }

    @Test
    void aGivenStartInThePastIsKept() {
        Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);
        service.add("DEMO-1", new WorklogRequest(30, twoDaysAgo, null), bob);
        assertThat(saved().getStartedAt()).isEqualTo(twoDaysAgo);
    }

    @Test
    void aStartInTheFutureIsRejected() {
        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);
        assertStatus(() -> service.add("DEMO-1", new WorklogRequest(30, tomorrow, null), bob), 400);
        verify(worklogs, never()).save(any());
    }

    @Test
    void aSmallClockSkewIsTolerated() {
        Instant aMinuteAhead = Instant.now().plus(1, ChronoUnit.MINUTES);
        WorklogResponse res = service.add("DEMO-1", new WorklogRequest(30, aMinuteAhead, null), bob);
        assertThat(res.minutes()).isEqualTo(30);
    }

    @Test
    void aBlankNoteIsStoredAsNull() {
        service.add("DEMO-1", new WorklogRequest(15, null, "   "), bob);
        assertThat(saved().getNote()).isNull();
    }

    @Test
    void viewersCannotLogTime() {
        assertStatus(() -> service.add("DEMO-1", new WorklogRequest(30, null, null), carol), 403);
        verify(worklogs, never()).save(any());
    }

    @Test
    void nonMembersCannotLogTime() {
        assertStatus(() -> service.add("DEMO-1", new WorklogRequest(30, null, null), dave), 404);
    }

    @Test
    void anUnknownIssueIs404() {
        when(issues.findByKey("DEMO-99")).thenReturn(Optional.empty());
        assertStatus(() -> service.add("DEMO-99", new WorklogRequest(30, null, null), bob), 404);
    }

    @Test
    void theIssueKeyIsCaseInsensitive() {
        WorklogResponse res = service.add("demo-1", new WorklogRequest(30, null, null), bob);
        assertThat(res.issueKey()).isEqualTo("DEMO-1");
    }

    //  list

    @Test
    void listSumsTheTimeAndShowsWhatTheCallerMayDo() {
        Worklog fromBob = worklogBy("w1", "u2", 30);
        Worklog fromAlice = worklogBy("w2", "u1", 90);
        when(worklogs.findByIssueId(eq("i1"), any(Sort.class))).thenReturn(List.of(fromBob, fromAlice));

        WorklogListResponse res = service.list("DEMO-1", carol);

        assertThat(res.totalMinutes()).isEqualTo(120);
        assertThat(res.totalTime()).isEqualTo("2h");
        assertThat(res.entries()).extracting(WorklogResponse::id).containsExactly("w1", "w2");
        assertThat(res.entries().get(0).author().email()).isEqualTo("bob@test.lt");
        assertThat(res.entries()).allSatisfy(e -> {
            assertThat(e.canEdit()).isFalse();
            assertThat(e.canDelete()).isFalse();
        });
    }

    @Test
    void listIsSortedNewestFirst() {
        when(worklogs.findByIssueId(eq("i1"), any(Sort.class))).thenReturn(List.of());

        service.list("DEMO-1", alice);

        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(worklogs).findByIssueId(eq("i1"), sort.capture());
        assertThat(sort.getValue().getOrderFor("startedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void adminsCanDeleteButNotEditOthersWorklogs() {
        Worklog fromBob = worklogBy("w1", "u2", 30);
        when(worklogs.findByIssueId(eq("i1"), any(Sort.class))).thenReturn(List.of(fromBob));

        WorklogResponse entry = service.list("DEMO-1", alice).entries().get(0);

        assertThat(entry.canEdit()).isFalse();
        assertThat(entry.canDelete()).isTrue();
    }

    @Test
    void anIssueWithoutWorklogsHasZeroTime() {
        when(worklogs.findByIssueId(eq("i1"), any(Sort.class))).thenReturn(List.of());

        WorklogListResponse res = service.list("DEMO-1", bob);

        assertThat(res.totalMinutes()).isZero();
        assertThat(res.totalTime()).isEqualTo("0m");
        assertThat(res.entries()).isEmpty();
    }

    @Test
    void nonMembersCannotListWorklogs() {
        assertStatus(() -> service.list("DEMO-1", dave), 404);
    }

    //  update

    @Test
    void authorsCanEditAndTheStartIsKeptWhenOmitted() {
        Worklog w = worklogBy("w1", "u2", 30);
        Instant originalStart = w.getStartedAt();

        WorklogResponse res = service.update("w1", new WorklogRequest(45, null, "  new note  "), bob);

        assertThat(w.getMinutes()).isEqualTo(45);
        assertThat(w.getNote()).isEqualTo("new note");
        assertThat(w.getStartedAt()).isEqualTo(originalStart);
        assertThat(w.getUpdatedAt()).isNotNull();
        assertThat(res.timeSpent()).isEqualTo("45m");
        assertThat(res.canEdit()).isTrue();
        verify(worklogs).save(w);
    }

    @Test
    void editingCanMoveTheStartDate() {
        Worklog w = worklogBy("w1", "u2", 30);
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);

        service.update("w1", new WorklogRequest(30, yesterday, null), bob);

        assertThat(w.getStartedAt()).isEqualTo(yesterday);
    }

    @Test
    void editingToAFutureStartIsRejected() {
        Worklog w = worklogBy("w1", "u2", 30);
        Instant originalStart = w.getStartedAt();
        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);

        assertStatus(() -> service.update("w1", new WorklogRequest(30, tomorrow, null), bob), 400);

        assertThat(w.getStartedAt()).isEqualTo(originalStart);
        verify(worklogs, never()).save(any());
    }

    @Test
    void onlyTheAuthorCanEdit() {
        Worklog w = worklogBy("w1", "u2", 30);

        assertStatus(() -> service.update("w1", new WorklogRequest(999, null, "hijack"), alice), 403);

        assertThat(w.getMinutes()).isEqualTo(30);
        verify(worklogs, never()).save(any());
    }

    @Test
    void anAuthorWhoBecameAViewerCannotEdit() {
        worklogBy("w1", "u3", 30);
        assertStatus(() -> service.update("w1", new WorklogRequest(45, null, null), carol), 403);
    }

    @Test
    void editingAnUnknownWorklogIs404() {
        when(worklogs.findById("nope")).thenReturn(Optional.empty());
        assertStatus(() -> service.update("nope", new WorklogRequest(45, null, null), bob), 404);
    }

    @Test
    void nonMembersCannotEdit() {
        worklogBy("w1", "u2", 30);
        assertStatus(() -> service.update("w1", new WorklogRequest(45, null, null), dave), 404);
    }

    //  delete

    @Test
    void authorsCanDeleteTheirOwnWorklog() {
        Worklog w = worklogBy("w1", "u2", 30);
        service.delete("w1", bob);
        verify(worklogs).delete(w);
    }

    @Test
    void aViewerCanDeleteTheirOwnWorklog() {
        Worklog w = worklogBy("w1", "u3", 30);
        service.delete("w1", carol);
        verify(worklogs).delete(w);
    }

    @Test
    void adminsCanDeleteAnyWorklog() {
        Worklog w = worklogBy("w1", "u2", 30);
        service.delete("w1", alice);
        verify(worklogs).delete(w);
    }

    @Test
    void membersCannotDeleteSomeoneElsesWorklog() {
        worklogBy("w1", "u1", 30);
        assertStatus(() -> service.delete("w1", bob), 403);
        verify(worklogs, never()).delete(any(Worklog.class));
    }

    @Test
    void nonMembersCannotDelete() {
        worklogBy("w1", "u2", 30);
        assertStatus(() -> service.delete("w1", dave), 404);
    }
}