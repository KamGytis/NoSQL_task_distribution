package lt.vu.tracker.service;

import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.User;
import lt.vu.tracker.repository.IssueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static lt.vu.tracker.TestData.assertStatus;
import static lt.vu.tracker.TestData.user;
import static lt.vu.tracker.model.enums.ProjectRole.VIEWER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchServiceTest {
    private final PermissionService permissions = mock(PermissionService.class);
    private final ElasticsearchGateway elasticsearch =
            mock(ElasticsearchGateway.class);
    private final IssueRepository issues = mock(IssueRepository.class);
    private final SearchService service =
            new SearchService(permissions, elasticsearch, issues);

    private final User current = user("u1", "search@test.lt");

    @Test
    void nonMembersCannotSearchOrSuggest() {
        when(permissions.requireRole("p1", "u1", VIEWER))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertStatus(() -> service.search("p1", "login", 0, 20, current), 404);
        assertStatus(() -> service.suggest("p1", "log", 10, current), 404);
        verifyNoInteractions(elasticsearch, issues);
    }

    @Test
    void rejectsInvalidInputBeforeQueryingElasticsearch() {
        assertStatus(() -> service.search("p1", " ", 0, 20, current), 400);
        assertStatus(() -> service.search("p1", "login", -1, 20, current), 400);
        assertStatus(() -> service.search("p1", "login", 1000, 20, current), 400);
        assertStatus(() -> service.suggest("p1", "login", 21, current), 400);
        verifyNoInteractions(elasticsearch);
    }

    @Test
    void preservesHitOrderAndOmitsMissingOrForeignIssues() {
        Issue first = issue("i1", "p1", "DEMO-1");
        Issue second = issue("i2", "p1", "DEMO-2");
        Issue foreign = issue("i3", "p2", "OTHER-1");

        when(elasticsearch.search(anyMap()))
                .thenReturn(List.of("i2", "missing", "i3", "i1"));
        when(issues.findAllById(anyIterable()))
                .thenReturn(List.of(first, second, foreign));

        assertThat(service.search("p1", "login", 0, 20, current))
                .extracting(response -> response.key())
                .containsExactly("DEMO-2", "DEMO-1");
    }

    @Test
    void suggestionsReturnCurrentKeyAndTitle() {
        Issue issue = issue("i1", "p1", "DEMO-1");
        when(elasticsearch.search(anyMap())).thenReturn(List.of("i1"));
        when(issues.findAllById(anyIterable())).thenReturn(List.of(issue));

        var result = service.suggest("p1", "log", 10, current);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().key()).isEqualTo("DEMO-1");
        assertThat(result.getFirst().title()).isEqualTo("Login");
    }

    private Issue issue(String id, String projectId, String key) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setProjectId(projectId);
        issue.setKey(key);
        issue.setTitle("Login");
        return issue;
    }
}