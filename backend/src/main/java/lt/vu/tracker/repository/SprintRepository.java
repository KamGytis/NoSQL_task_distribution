package lt.vu.tracker.repository;

import lt.vu.tracker.model.Sprint;
import lt.vu.tracker.model.enums.SprintStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SprintRepository extends MongoRepository<Sprint, String> {
    List<Sprint> findByProjectIdOrderByCreatedAtDesc(String projectId);

    boolean existsByProjectIdAndStatus(String projectId, SprintStatus status);
}
