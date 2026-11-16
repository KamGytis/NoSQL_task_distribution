package lt.vu.tracker.repository;

import lt.vu.tracker.model.Project;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProjectRepository extends MongoRepository<Project, String> {
    boolean existsByKey(String key);
    List<Project> findByMembersUserId(String userId);
}