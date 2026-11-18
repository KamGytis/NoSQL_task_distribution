package lt.vu.tracker.repository;

import lt.vu.tracker.model.Worklog;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface WorklogRepository extends MongoRepository<Worklog, String> {
    List<Worklog> findByIssueId(String issueId, Sort sort);
    void deleteByIssueId(String issueId);
    void deleteByProjectId(String projectId);
}