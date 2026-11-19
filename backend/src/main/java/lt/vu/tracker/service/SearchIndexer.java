package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "search.sync.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SearchIndexer {
    private final SearchIndexingService indexing;

    @Scheduled(
            fixedDelayString = "${search.sync.delay-ms:5000}",
            initialDelayString = "${search.sync.delay-ms:5000}"
    )
    public void sync() {
        indexing.syncPending();
    }
}