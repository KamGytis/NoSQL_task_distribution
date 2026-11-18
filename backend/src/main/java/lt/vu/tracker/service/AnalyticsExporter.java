package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "analytics.export.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AnalyticsExporter {
    private final AnalyticsService analytics;

    @Scheduled(
            fixedDelayString = "${analytics.export.delay-ms:5000}",
            initialDelayString = "${analytics.export.delay-ms:5000}"
    )
    public void export() {
        analytics.exportPendingEvents();
    }
}