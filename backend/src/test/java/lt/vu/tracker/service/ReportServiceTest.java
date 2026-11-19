package lt.vu.tracker.service;

import lt.vu.tracker.dto.response.DailyActivityResponse;
import lt.vu.tracker.dto.response.EventCountResponse;
import lt.vu.tracker.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import static lt.vu.tracker.TestData.assertStatus;
import static lt.vu.tracker.TestData.user;
import static lt.vu.tracker.model.enums.ProjectRole.VIEWER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    private final JdbcTemplate clickHouse = mock(JdbcTemplate.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final ProjectReadCache cache = mock(ProjectReadCache.class);
    private final ReportService service =
            new ReportService(clickHouse, permissions, cache);

    private final User current = user("u1", "report@test.lt");
    private final LocalDate from = LocalDate.of(2026, 11, 1);
    private final LocalDate to = LocalDate.of(2026, 11, 2);

    @BeforeEach
    void setUp() {
        when(cache.read(
                eq("p1"),
                anyString(),
                eq(EventCountResponse[].class),
                any()
        )).thenAnswer(call ->
                call.<Supplier<List<EventCountResponse>>>getArgument(3).get());

        when(cache.read(
                eq("p1"),
                anyString(),
                eq(DailyActivityResponse[].class),
                any()
        )).thenAnswer(call ->
                call.<Supplier<List<DailyActivityResponse>>>getArgument(3).get());
    }

    @Test
    void eventsQueryUsesProjectAndInclusiveUtcDateRange() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("event_type")).thenReturn("ISSUE_CREATED");
        when(row.getLong("event_count")).thenReturn(3L);

        when(clickHouse.query(
                eq(ReportService.EVENT_COUNTS_SQL),
                any(RowMapper.class),
                eq("p1"),
                eq(startMillis(from)),
                eq(startMillis(to.plusDays(1)))
        )).thenAnswer(call -> {
            RowMapper<EventCountResponse> mapper = call.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });

        assertThat(service.events("p1", from, to, current))
                .containsExactly(new EventCountResponse("ISSUE_CREATED", 3));

        verify(permissions).requireRole("p1", "u1", VIEWER);
        verify(cache).read(
                eq("p1"),
                eq("report-events:v1:2026-11-01:2026-11-02"),
                eq(EventCountResponse[].class),
                any()
        );
    }

    @Test
    void dailyQueryMapsDateTypeAndCount() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("event_day")).thenReturn("2026-11-01");
        when(row.getString("event_type")).thenReturn("ISSUE_TRANSITIONED");
        when(row.getLong("event_count")).thenReturn(2L);

        when(clickHouse.query(
                eq(ReportService.DAILY_ACTIVITY_SQL),
                any(RowMapper.class),
                eq("p1"),
                eq(startMillis(from)),
                eq(startMillis(to.plusDays(1)))
        )).thenAnswer(call -> {
            RowMapper<DailyActivityResponse> mapper = call.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });

        assertThat(service.activity("p1", from, to, current))
                .containsExactly(new DailyActivityResponse(
                        from, "ISSUE_TRANSITIONED", 2
                ));
    }

    @Test
    void nonMembersCannotReadCachedReports() {
        when(permissions.requireRole("p1", "u1", VIEWER))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertStatus(() -> service.events("p1", from, to, current), 404);
        assertStatus(() -> service.activity("p1", from, to, current), 404);

        verifyNoInteractions(cache, clickHouse);
    }

    @Test
    void invalidRangesNeverReadCacheOrClickHouse() {
        assertStatus(() -> service.events("p1", to, from, current), 400);
        assertStatus(() -> service.events(
                "p1", from, from.plusDays(366), current
        ), 400);
        assertStatus(() -> service.activity("p1", null, to, current), 400);

        verifyNoInteractions(cache, clickHouse);
    }

    @Test
    void cachedReportDoesNotQueryClickHouse() {
        var cached = List.of(new EventCountResponse("ISSUE_CREATED", 5));

        when(cache.read(
                eq("p1"),
                anyString(),
                eq(EventCountResponse[].class),
                any()
        )).thenReturn(cached);

        assertThat(service.events("p1", from, to, current)).isEqualTo(cached);
        verifyNoInteractions(clickHouse);
    }

    private long startMillis(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}