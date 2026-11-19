package lt.vu.tracker.service;

import lt.vu.tracker.dto.response.DailyActivityResponse;
import lt.vu.tracker.dto.response.EventCountResponse;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.ProjectRole;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReportService {
    static final String EVENT_COUNTS_SQL = """
            SELECT event_type, count() AS event_count
            FROM analytics_events FINAL
            WHERE project_id = ?
              AND occurred_at_ms >= ?
              AND occurred_at_ms < ?
            GROUP BY event_type
            ORDER BY event_type
            """;

    static final String DAILY_ACTIVITY_SQL = """
            SELECT
                toString(toDate(occurred_at)) AS event_day,
                event_type,
                count() AS event_count
            FROM analytics_events FINAL
            WHERE project_id = ?
              AND occurred_at_ms >= ?
              AND occurred_at_ms < ?
            GROUP BY event_day, event_type
            ORDER BY event_day, event_type
            """;

    private final JdbcTemplate clickHouse;
    private final PermissionService permissions;
    private final ProjectReadCache cache;

    public ReportService(
            @Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouse,
            PermissionService permissions,
            ProjectReadCache cache
    ) {
        this.clickHouse = clickHouse;
        this.permissions = permissions;
        this.cache = cache;
    }

    public List<EventCountResponse> events(
            String projectId,
            LocalDate from,
            LocalDate to,
            User current
    ) {
        permissions.requireRole(projectId, current.getId(), ProjectRole.VIEWER);
        validateRange(from, to);

        String view = "report-events:v1:" + from + ":" + to;

        return cache.read(
                projectId,
                view,
                EventCountResponse[].class,
                () -> clickHouse.query(
                        EVENT_COUNTS_SQL,
                        (row, index) -> new EventCountResponse(
                                row.getString("event_type"),
                                row.getLong("event_count")
                        ),
                        projectId,
                        startMillis(from),
                        startMillis(to.plusDays(1))
                )
        );
    }

    public List<DailyActivityResponse> activity(
            String projectId,
            LocalDate from,
            LocalDate to,
            User current
    ) {
        permissions.requireRole(projectId, current.getId(), ProjectRole.VIEWER);
        validateRange(from, to);

        String view = "report-daily:v1:" + from + ":" + to;

        return cache.read(
                projectId,
                view,
                DailyActivityResponse[].class,
                () -> clickHouse.query(
                        DAILY_ACTIVITY_SQL,
                        (row, index) -> new DailyActivityResponse(
                                LocalDate.parse(row.getString("event_day")),
                                row.getString("event_type"),
                                row.getLong("event_count")
                        ),
                        projectId,
                        startMillis(from),
                        startMillis(to.plusDays(1))
                )
        );
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw badRequest("Both from and to are required");
        }
        if (from.isBefore(LocalDate.of(1970, 1, 1))
                || to.isAfter(LocalDate.of(2299, 12, 31))) {
            throw badRequest("Dates must be between 1970-01-01 and 2299-12-31");
        }
        if (to.isBefore(from)) {
            throw badRequest("To must be on or after from");
        }
        if (ChronoUnit.DAYS.between(from, to) >= 366) {
            throw badRequest("Date range must contain at most 366 days");
        }
    }

    private long startMillis(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}