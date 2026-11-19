package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lt.vu.tracker.model.Issue;
import lt.vu.tracker.model.IssueHistory;
import lt.vu.tracker.repository.IssueRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchIndexingService {
    private final MongoTemplate mongo;
    private final IssueRepository issues;
    private final ElasticsearchGateway elasticsearch;

    public int syncPending() {
        Query pending = Query.query(
                        Criteria.where("searchIndexed").ne(true)
                ).with(Sort.by(Sort.Direction.ASC, "createdAt", "id"))
                .limit(100);

        int indexed = 0;

        for (IssueHistory entry : mongo.find(pending, IssueHistory.class)) {
            try {
                Issue issue = issues.findById(entry.getIssueId()).orElse(null);

                if (issue == null) {
                    elasticsearch.index(
                            entry.getIssueId(),
                            Long.MAX_VALUE,
                            Map.of(
                                    "projectId", entry.getProjectId(),
                                    "deleted", true
                            )
                    );
                } else {
                    long version = issue.getVersion() == null
                            ? 1 : Math.addExact(issue.getVersion(), 1);

                    elasticsearch.index(
                            issue.getId(),
                            version,
                            Map.of(
                                    "projectId", issue.getProjectId(),
                                    "deleted", false,
                                    "key", issue.getKey(),
                                    "title", issue.getTitle(),
                                    "description", issue.getDescription() == null
                                            ? "" : issue.getDescription()
                            )
                    );
                }

                mongo.updateFirst(
                        Query.query(Criteria.where("id").is(entry.getId())),
                        Update.update("searchIndexed", true),
                        IssueHistory.class
                );
                indexed++;
            } catch (DataAccessException | ResponseStatusException exception) {
                log.warn("Search indexing for history {} will be retried",
                        entry.getId(), exception);
            }
        }

        return indexed;
    }
}