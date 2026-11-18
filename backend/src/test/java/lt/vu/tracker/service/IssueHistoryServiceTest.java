package lt.vu.tracker.service;

import lt.vu.tracker.dto.response.HistoryEntryResponse;
import lt.vu.tracker.dto.response.HistoryPageResponse;
import lt.vu.tracker.model.FieldChange;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.IssueHistory;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.HistoryAction;
import lt.vu.tracker.model.enums.IssueStatus;
import lt.vu.tracker.repository.IssueHistoryRepository;
import lt.vu.tracker.repository.IssueRepository;
import lt.vu.tracker.repository.ProjectRepository;
import lt.vu.tracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
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

class IssueHistoryServiceTest {
    private final IssueHistoryRepository history = mock(IssueHistoryRepository.class);
    private final IssueRepository issues = mock(IssueRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final IssueHistoryService service =
            new IssueHistoryService(history, issues, users, new PermissionService(projects));

    private final User alice = user("u1", "alice@test.lt");   // ADMIN
    private final User bob = user("u2", "bob@test.lt");       // MEMBER
    private final User carol = user("u3", "carol@test.lt");   // VIEWER
    private final User dave = user("u4", "dave@test.lt");     // not a member
    private final List<User> everyone = List.of(alice, bob, carol, dave);

    @BeforeEach
    void setUp() {
        when(projects.findById("p1")).thenReturn(Optional.of(
                project("p1", member("u1", ADMIN), member("u2", MEMBER), member("u3", VIEWER))));
        when(issues.findByKey("DEMO-1")).thenReturn(Optional.of(issue("Fix login", "desc", IssueStatus.TODO)));
        when(users.findAllById(anyIterable())).thenAnswer(inv -> {
            Iterable<String> given = inv.getArgument(0);
            Set<String> ids = new HashSet<>();
            given.forEach(ids::add);
            return everyone.stream().filter(u -> ids.contains(u.getId())).toList();
        });
    }

    private Issue issue(String title, String description, IssueStatus status) {
        Issue i = new Issue();
        i.setId("i1");
        i.setKey("DEMO-1");
        i.setProjectId("p1");
        i.setTitle(title);
        i.setDescription(description);
        i.setStatus(status);
        return i;
    }

    private IssueHistory entry(String id, String actorId, HistoryAction action, FieldChange... changes) {
        IssueHistory h = new IssueHistory();
        h.setId(id);
        h.setIssueId("i1");
        h.setIssueKey("DEMO-1");
        h.setProjectId("p1");
        h.setActorId(actorId);
        h.setAction(action);
        h.setChanges(List.of(changes));
        h.setCreatedAt(Instant.now());
        return h;
    }

    private IssueHistory written() {
        ArgumentCaptor<IssueHistory> captor = ArgumentCaptor.forClass(IssueHistory.class);
        verify(history).insert(captor.capture());
        return captor.getValue();
    }

    //  writing

    @Test
    void creatingAnIssueRecordsTitleDescriptionAndStatus() {
        service.recordCreated(issue("Fix login", "desc", IssueStatus.TODO), bob);

        IssueHistory h = written();
        assertThat(h.getAction()).isEqualTo(HistoryAction.CREATED);
        assertThat(h.getActorId()).isEqualTo("u2");
        assertThat(h.getIssueId()).isEqualTo("i1");
        assertThat(h.getIssueKey()).isEqualTo("DEMO-1");
        assertThat(h.getProjectId()).isEqualTo("p1");
        assertThat(h.getCreatedAt()).isNotNull();
        assertThat(h.getChanges()).extracting(FieldChange::getField)
                .containsExactly("title", "description", "status");
        FieldChange status = h.getChanges().get(2);
        assertThat(status.getOldValue()).isNull();
        assertThat(status.getNewValue()).isEqualTo("TODO");
    }

    @Test
    void creatingWithoutADescriptionSkipsIt() {
        service.recordCreated(issue("Fix login", "   ", IssueStatus.TODO), bob);
        assertThat(written().getChanges()).extracting(FieldChange::getField)
                .containsExactly("title", "status");
    }

    @Test
    void updatingRecordsOnlyWhatChanged() {
        service.recordUpdated(issue("New title", "same", IssueStatus.TODO), "Old title", "same", bob);

        IssueHistory h = written();
        assertThat(h.getAction()).isEqualTo(HistoryAction.UPDATED);
        assertThat(h.getChanges()).hasSize(1);
        assertThat(h.getChanges().get(0).getField()).isEqualTo("title");
        assertThat(h.getChanges().get(0).getOldValue()).isEqualTo("Old title");
        assertThat(h.getChanges().get(0).getNewValue()).isEqualTo("New title");
    }

    @Test
    void updatingBothFieldsRecordsBoth() {
        service.recordUpdated(issue("B", "new text", IssueStatus.TODO), "A", "old text", bob);
        assertThat(written().getChanges()).extracting(FieldChange::getField)
                .containsExactly("title", "description");
    }

    @Test
    void anUpdateThatChangesNothingWritesNothing() {
        service.recordUpdated(issue("Same", "same", IssueStatus.TODO), "Same", "same", bob);
        verify(history, never()).insert(any(IssueHistory.class));
    }

    @Test
    void nullAndBlankDescriptionsAreTheSame() {
        service.recordUpdated(issue("Same", "  ", IssueStatus.TODO), "Same", null, bob);
        verify(history, never()).insert(any(IssueHistory.class));
    }

    @Test
    void clearingADescriptionIsAChange() {
        service.recordUpdated(issue("Same", null, IssueStatus.TODO), "Same", "was here", bob);
        FieldChange change = written().getChanges().get(0);
        assertThat(change.getField()).isEqualTo("description");
        assertThat(change.getOldValue()).isEqualTo("was here");
        assertThat(change.getNewValue()).isNull();
    }

    @Test
    void aTransitionRecordsFromAndTo() {
        service.recordTransition(issue("Fix login", "desc", IssueStatus.IN_PROGRESS),
                IssueStatus.TODO, IssueStatus.IN_PROGRESS, bob);

        IssueHistory h = written();
        assertThat(h.getAction()).isEqualTo(HistoryAction.TRANSITIONED);
        assertThat(h.getChanges()).hasSize(1);
        assertThat(h.getChanges().get(0).getField()).isEqualTo("status");
        assertThat(h.getChanges().get(0).getOldValue()).isEqualTo("TODO");
        assertThat(h.getChanges().get(0).getNewValue()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void deletingKeepsTheTitle() {
        service.recordDeleted(issue("Fix login", "desc", IssueStatus.DONE), alice);

        IssueHistory h = written();
        assertThat(h.getAction()).isEqualTo(HistoryAction.DELETED);
        assertThat(h.getChanges().get(0).getOldValue()).isEqualTo("Fix login");
        assertThat(h.getChanges().get(0).getNewValue()).isNull();
    }

    //  reading one issue

    @Test
    void anyMemberCanReadAnIssuesHistoryNewestFirst() {
        when(history.findByIssueId(eq("i1"), any(Sort.class))).thenReturn(List.of(
                entry("h2", "u2", HistoryAction.TRANSITIONED, new FieldChange("status", "TODO", "IN_PROGRESS")),
                entry("h1", "u2", HistoryAction.CREATED, new FieldChange("title", null, "Fix login"))));

        List<HistoryEntryResponse> result = service.listForIssue("DEMO-1", carol);

        assertThat(result).extracting(HistoryEntryResponse::id).containsExactly("h2", "h1");
        assertThat(result.get(0).actor().email()).isEqualTo("bob@test.lt");
        assertThat(result.get(0).action()).isEqualTo(HistoryAction.TRANSITIONED);

        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(history).findByIssueId(eq("i1"), sort.capture());
        assertThat(sort.getValue().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void anActorWhoNoLongerExistsHasNoName() {
        when(history.findByIssueId(eq("i1"), any(Sort.class)))
                .thenReturn(List.of(entry("h1", "u9", HistoryAction.CREATED)));
        assertThat(service.listForIssue("DEMO-1", alice).get(0).actor()).isNull();
    }

    @Test
    void theIssueKeyIsCaseInsensitive() {
        when(history.findByIssueId(eq("i1"), any(Sort.class))).thenReturn(List.of());
        assertThat(service.listForIssue("demo-1", alice)).isEmpty();
    }

    @Test
    void nonMembersCannotReadAnIssuesHistory() {
        assertStatus(() -> service.listForIssue("DEMO-1", dave), 404);
    }

    @Test
    void anUnknownIssueIs404() {
        when(issues.findByKey("DEMO-99")).thenReturn(Optional.empty());
        assertStatus(() -> service.listForIssue("DEMO-99", alice), 404);
    }

    //  reading the project feed

    @Test
    void theProjectFeedIsPagedWithTheTotals() {
        when(history.findByProjectId(eq("p1"), any(Pageable.class))).thenAnswer(inv ->
                new PageImpl<>(List.of(entry("h1", "u2", HistoryAction.CREATED)), inv.<Pageable>getArgument(1), 45));

        HistoryPageResponse res = service.listForProject("p1", 1, 20, carol);

        assertThat(res.items()).hasSize(1);
        assertThat(res.page()).isEqualTo(1);
        assertThat(res.size()).isEqualTo(20);
        assertThat(res.totalItems()).isEqualTo(45);
        assertThat(res.totalPages()).isEqualTo(3);
    }

    @Test
    void theFeedClampsPageAndSizeAndSortsNewestFirst() {
        when(history.findByProjectId(eq("p1"), any(Pageable.class))).thenAnswer(inv ->
                new PageImpl<>(List.<IssueHistory>of(), inv.<Pageable>getArgument(1), 0));

        service.listForProject("p1", -5, 1000, alice);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(history).findByProjectId(eq("p1"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void aSizeOfZeroBecomesOne() {
        when(history.findByProjectId(eq("p1"), any(Pageable.class))).thenAnswer(inv ->
                new PageImpl<>(List.<IssueHistory>of(), inv.<Pageable>getArgument(1), 0));

        service.listForProject("p1", 0, 0, alice);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(history).findByProjectId(eq("p1"), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void nonMembersCannotReadTheProjectFeed() {
        assertStatus(() -> service.listForProject("p1", 0, 20, dave), 404);
        verify(history, never()).findByProjectId(any(), any(Pageable.class));
    }
}