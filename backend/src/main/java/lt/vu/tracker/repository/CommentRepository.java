package lt.vu.tracker.repository;

import lt.vu.tracker.model.Comment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CommentRepository extends MongoRepository<Comment, String> {
    List<Comment> findByIssueIdOrderByCreatedAtAsc(String issueId);
    void deleteByIssueId(String issueId);
    void deleteByProjectId(String projectId);
}