package lt.vu.tracker.service;

import lt.vu.tracker.dto.request.SavedFilterRequest;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.SavedFilter;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.IssueStatus;
import lt.vu.tracker.repository.SavedFilterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static lt.vu.tracker.TestData.assertStatus;
import static lt.vu.tracker.TestData.user;
import static lt.vu.tracker.model.enums.ProjectRole.VIEWER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SavedFilterServiceTest {
    private final SavedFilterRepository filters = mock(SavedFilterRepository.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final SavedFilterService service =
            new SavedFilterService(filters, permissions, mongo);

    private final User owner = user("u1", "owner@test.lt");
    private final User other = user("u2", "other@test.lt");

    @BeforeEach
    void setUp() {
        when(filters.insert(any(SavedFilter.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(filters.save(any(SavedFilter.class)))
                .thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsFilterForCurrentUser() {
        var request = new SavedFilterRequest(
                " My tasks ", "p1", IssueStatus.TODO, "u1", " [demo] "
        );

        var response = service.create(request, owner);

        var captured = ArgumentCaptor.forClass(SavedFilter.class);
        verify(filters).insert(captured.capture());
        verify(permissions).requireRole("p1", "u1", VIEWER);

        assertThat(captured.getValue().getOwnerId()).isEqualTo("u1");
        assertThat(response.name()).isEqualTo("My tasks");
        assertThat(response.titleContains()).isEqualTo("[demo]");
    }

    @Test
    void listsOnlyCurrentUsersFilters() {
        SavedFilter filter = ownedFilter();
        when(filters.findByOwnerIdOrderByCreatedAtDescIdDesc("u1"))
                .thenReturn(List.of(filter));

        assertThat(service.list(owner)).extracting(item -> item.id())
                .containsExactly("f1");
        verify(filters).findByOwnerIdOrderByCreatedAtDescIdDesc("u1");
    }

    @Test
    void rejectsAccessByAnotherUser() {
        assertStatus(() -> service.get("f1", other), 404);
        assertStatus(() -> service.update(
                "f1",
                new SavedFilterRequest("Changed", "p1", null, null, null),
                other
        ), 404);
        assertStatus(() -> service.delete("f1", other), 404);
        assertStatus(() -> service.execute("f1", 0, 20, other), 404);

        verify(filters, never()).save(any(SavedFilter.class));
        verify(filters, never()).delete(any(SavedFilter.class));
        verifyNoInteractions(mongo);
    }

    @Test
    void updateKeepsOwnerAndClearsOptionalCriteria() {
        SavedFilter filter = ownedFilter();
        filter.setStatus(IssueStatus.TODO);
        filter.setAssigneeId("u1");
        filter.setTitleContains("Old");

        service.update("f1",
                new SavedFilterRequest("Updated", "p1", null, null, " "),
                owner);

        assertThat(filter.getOwnerId()).isEqualTo("u1");
        assertThat(filter.getName()).isEqualTo("Updated");
        assertThat(filter.getStatus()).isNull();
        assertThat(filter.getAssigneeId()).isNull();
        assertThat(filter.getTitleContains()).isNull();
        verify(permissions).requireRole("p1", "u1", VIEWER);
    }

    @Test
    void checksCurrentMembershipBeforeExecuting() {
        ownedFilter();
        when(permissions.requireRole("p1", "u1", VIEWER))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertStatus(() -> service.execute("f1", 0, 20, owner), 404);
        verifyNoInteractions(mongo);
    }

    @Test
    void appliesCriteriaAndPaginationInMongoQuery() {
        SavedFilter filter = ownedFilter();
        filter.setStatus(IssueStatus.TODO);
        filter.setAssigneeId("u1");
        filter.setTitleContains("[demo]");

        when(mongo.find(any(Query.class), eq(Issue.class)))
                .thenReturn(List.of());

        service.execute("f1", 1, 10, owner);

        var captured = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(captured.capture(), eq(Issue.class));
        Query query = captured.getValue();

        assertThat(query.getQueryObject().getString("projectId")).isEqualTo("p1");
        assertThat(query.getQueryObject().get("status")).isEqualTo(IssueStatus.TODO);
        assertThat(query.getQueryObject().getString("assigneeId")).isEqualTo("u1");
        var titlePattern = (java.util.regex.Pattern) query.getQueryObject().get("title");

        assertThat(titlePattern.pattern()).isEqualTo("\\Q[demo]\\E");
        assertThat(titlePattern.matcher("Login [DEMO] problem").find()).isTrue();
        assertThat(titlePattern.matcher("Login demo problem").find()).isFalse();
        assertThat(query.getSkip()).isEqualTo(10);
        assertThat(query.getLimit()).isEqualTo(10);
    }

    @Test
    void rejectsInvalidPagination() {
        assertStatus(() -> service.execute("f1", -1, 20, owner), 400);
        assertStatus(() -> service.execute("f1", 0, 0, owner), 400);
        assertStatus(() -> service.execute("f1", 0, 101, owner), 400);
        verifyNoInteractions(mongo);
    }

    @Test
    void deletesOwnedFilter() {
        SavedFilter filter = ownedFilter();

        service.delete("f1", owner);

        verify(filters).delete(filter);
    }

    private SavedFilter ownedFilter() {
        SavedFilter filter = new SavedFilter();
        filter.setId("f1");
        filter.setOwnerId("u1");
        filter.setProjectId("p1");
        filter.setName("My filter");
        when(filters.findByIdAndOwnerId("f1", "u1"))
                .thenReturn(Optional.of(filter));
        return filter;
    }
}