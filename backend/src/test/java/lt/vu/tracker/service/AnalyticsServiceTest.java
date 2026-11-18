package lt.vu.tracker.service;

import lt.vu.tracker.model.IssueHistory;
import lt.vu.tracker.model.enums.HistoryAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {
    private final JdbcTemplate clickHouse = mock(JdbcTemplate.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final AnalyticsService service = new AnalyticsService(
            clickHouse, mongo, JsonMapper.builder().build()
    );

    private final IssueHistory entry = new IssueHistory();

    @BeforeEach
    void setUp() {
        entry.setId("event-1");
        entry.setProjectId("p1");
        entry.setIssueId("i1");
        entry.setIssueKey("DEMO-1");
        entry.setActorId("u1");
        entry.setAction(HistoryAction.CREATED);
        entry.setCreatedAt(Instant.parse("2026-11-18T10:00:00Z"));

        when(mongo.find(any(Query.class), eq(IssueHistory.class)))
                .thenReturn(List.of(entry));
    }

    @Test
    void writesEventAndMarksItExportedAfterSuccess() {
        assertThat(service.exportPendingEvents()).isEqualTo(1);

        var order = inOrder(clickHouse, mongo);
        order.verify(mongo).find(any(Query.class), eq(IssueHistory.class));
        order.verify(clickHouse).update(
                AnalyticsService.INSERT_EVENT,
                "event-1", "p1", "i1", "DEMO-1", "u1",
                "ISSUE_CREATED", "[]", entry.getCreatedAt().toEpochMilli()
        );

        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);

        order.verify(mongo).updateFirst(
                query.capture(), update.capture(), eq(IssueHistory.class)
        );

        assertThat(query.getValue().getQueryObject().getString("id"))
                .isEqualTo("event-1");
        assertThat(update.getValue().getUpdateObject().toJson())
                .contains("analyticsExported", "true");
    }

    @Test
    void failedWriteLeavesEventPendingForAnotherAttempt() {
        when(clickHouse.update(
                AnalyticsService.INSERT_EVENT,
                "event-1", "p1", "i1", "DEMO-1", "u1",
                "ISSUE_CREATED", "[]", entry.getCreatedAt().toEpochMilli()
        )).thenThrow(new DataAccessResourceFailureException("Unavailable"))
                .thenReturn(1);

        assertThat(service.exportPendingEvents()).isZero();

        verify(mongo, never()).updateFirst(
                any(Query.class), any(Update.class), eq(IssueHistory.class)
        );

        assertThat(service.exportPendingEvents()).isEqualTo(1);

        verify(mongo).updateFirst(
                any(Query.class), any(Update.class), eq(IssueHistory.class)
        );
    }

    @Test
    void includesOlderHistoryWithoutAnExportFlag() {
        service.exportPendingEvents();

        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(IssueHistory.class));

        var condition = (org.bson.Document)
                query.getValue().getQueryObject().get("analyticsExported");

        assertThat(condition.get("$ne")).isEqualTo(true);
        assertThat(query.getValue().getLimit()).isEqualTo(100);
    }

    @Test
    void emptyBatchDoesNotWriteToClickHouse() {
        when(mongo.find(any(Query.class), eq(IssueHistory.class)))
                .thenReturn(List.of());

        assertThat(service.exportPendingEvents()).isZero();
        verifyNoInteractions(clickHouse);
    }
}