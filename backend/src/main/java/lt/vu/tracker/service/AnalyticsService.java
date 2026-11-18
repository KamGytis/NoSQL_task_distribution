package lt.vu.tracker.service;

import lombok.extern.slf4j.Slf4j;
import lt.vu.tracker.model.IssueHistory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@Slf4j
public class AnalyticsService {
    static final String INSERT_EVENT = """
            INSERT INTO analytics_events
            (
                event_id,
                project_id,
                issue_id,
                issue_key,
                actor_id,
                event_type,
                changes_json,
                occurred_at_ms
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate clickHouse;
    private final MongoTemplate mongo;
    private final JsonMapper json;

    public AnalyticsService(
            @Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouse,
            MongoTemplate mongo,
            JsonMapper json
    ) {
        this.clickHouse = clickHouse;
        this.mongo = mongo;
        this.json = json;
    }

    public int exportPendingEvents() {
        Query pending = Query.query(
                        Criteria.where("analyticsExported").ne(true)
                ).with(Sort.by(Sort.Direction.ASC, "createdAt", "id"))
                .limit(100);

        int exported = 0;

        for (IssueHistory entry : mongo.find(pending, IssueHistory.class)) {
            try {
                record(entry);

                mongo.updateFirst(
                        Query.query(Criteria.where("id").is(entry.getId())),
                        Update.update("analyticsExported", true),
                        IssueHistory.class
                );

                exported++;
            } catch (DataAccessException | JacksonException exception) {
                log.warn(
                        "Analytics event {} will be retried",
                        entry.getId(),
                        exception
                );
            }
        }

        return exported;
    }

    public void record(IssueHistory entry) {
        clickHouse.update(
                INSERT_EVENT,
                entry.getId(),
                entry.getProjectId(),
                entry.getIssueId(),
                entry.getIssueKey(),
                entry.getActorId(),
                "ISSUE_" + entry.getAction().name(),
                json.writeValueAsString(entry.getChanges()),
                entry.getCreatedAt().toEpochMilli()
        );
    }
}