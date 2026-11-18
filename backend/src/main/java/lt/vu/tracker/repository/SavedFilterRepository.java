package lt.vu.tracker.repository;

import lt.vu.tracker.model.SavedFilter;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SavedFilterRepository extends MongoRepository<SavedFilter, String> {
    List<SavedFilter> findByOwnerIdOrderByCreatedAtDescIdDesc(String ownerId);

    Optional<SavedFilter> findByIdAndOwnerId(String id, String ownerId);
}