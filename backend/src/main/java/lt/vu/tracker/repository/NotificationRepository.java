package lt.vu.tracker.repository;

import lt.vu.tracker.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    Page<Notification> findByUserId(String userId, Pageable pageable);
    Page<Notification> findByUserIdAndRead(String userId, boolean read, Pageable pageable);
    List<Notification> findAllByUserIdAndRead(String userId, boolean read);
    long countByUserIdAndRead(String userId, boolean read);
}