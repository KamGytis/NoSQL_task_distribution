package lt.vu.tracker.service;

import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.IssueHistory;
import lt.vu.tracker.repository.IssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchIndexingServiceTest {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final IssueRepository issues = mock(IssueRepository.class);
    private final ElasticsearchGateway elasticsearch =
            mock(ElasticsearchGateway.class);
    private final SearchIndexingService service =
            new SearchIndexingService(mongo, issues, elasticsearch);

    @BeforeEach
    void setUp() {
        IssueHistory entry = new IssueHistory();
        entry.setId("h1");
        entry.setIssueId("i1");
        entry.setProjectId("p1");

        when(mongo.find(any(Query.class), eq(IssueHistory.class)))
                .thenReturn(List.of(entry));
    }

    @Test
    void indexesCurrentIssueWithItsVersion() {
        Issue issue = new Issue();
        issue.setId("i1");
        issue.setProjectId("p1");
        issue.setKey("DEMO-1");
        issue.setTitle("Current title");
        issue.setVersion(2L);
        when(issues.findById("i1")).thenReturn(Optional.of(issue));

        assertThat(service.syncPending()).isEqualTo(1);

        verify(elasticsearch).index("i1", 3, Map.of(
                "projectId", "p1",
                "deleted", false,
                "key", "DEMO-1",
                "title", "Current title",
                "description", ""
        ));
        verify(mongo).updateFirst(
                any(Query.class), any(Update.class), eq(IssueHistory.class)
        );
    }

    @Test
    void missingIssueProducesPermanentDeletionMarker() {
        when(issues.findById("i1")).thenReturn(Optional.empty());

        assertThat(service.syncPending()).isEqualTo(1);

        verify(elasticsearch).index(
                "i1", Long.MAX_VALUE,
                Map.of("projectId", "p1", "deleted", true)
        );
    }

    @Test
    void failureLeavesHistoryPending() {
        when(issues.findById("i1")).thenReturn(Optional.empty());
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
                .when(elasticsearch).index(anyString(), anyLong(), anyMap());

        assertThat(service.syncPending()).isZero();

        verify(mongo, never()).updateFirst(
                any(Query.class), any(Update.class), eq(IssueHistory.class)
        );
    }
}