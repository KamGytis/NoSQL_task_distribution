package lt.vu.tracker.repository;

import lt.vu.tracker.model.IssueHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface IssueHistoryRepository extends MongoRepository<IssueHistory, String> {
    List<IssueHistory> findByIssueId(String issueId, Sort sort);
    Page<IssueHistory> findByProjectId(String projectId, Pageable pageable);
}