package lt.vu.tracker.repository;

import lt.vu.tracker.model.Issue;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface IssueRepository extends MongoRepository<Issue, String> {
    Optional<Issue> findByKey(String key);

    List<Issue> findByProjectIdOrderByCreatedAtDesc(String projectId);
    List<Issue> findByProjectIdAndSprintIdIsNullOrderByCreatedAtDesc(String projectId);
    List<Issue> findByProjectIdAndSprintIdOrderByCreatedAtDesc(String projectId, String sprintId);

}
