package lt.vu.tracker.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vu.tracker.dto.response.DailyActivityResponse;
import lt.vu.tracker.dto.response.EventCountResponse;
import lt.vu.tracker.security.CurrentUser;
import lt.vu.tracker.service.ReportService;
import org.springframework.dao.DataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {
    private final ReportService reports;
    private final CurrentUser currentUser;

    @GetMapping("/events")
    public List<EventCountResponse> events(
            @PathVariable String projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        return reports.events(projectId, from, to, currentUser.get());
    }

    @GetMapping("/activity")
    public List<DailyActivityResponse> activity(
            @PathVariable String projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        return reports.activity(projectId, from, to, currentUser.get());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleReportFailure(
            DataAccessException exception
    ) {
        log.warn("Report query failed", exception);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "Reports are temporarily unavailable"));
    }
}