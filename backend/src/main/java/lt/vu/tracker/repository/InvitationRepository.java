package lt.vu.tracker.repository;

import lt.vu.tracker.model.Invitation;
import lt.vu.tracker.model.enums.InvitationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InvitationRepository extends MongoRepository<Invitation, String> {
    List<Invitation> findByInvitedUserIdAndStatus(String userId, InvitationStatus status);
    boolean existsByProjectIdAndInvitedUserIdAndStatus(String projectId, String userId, InvitationStatus status);
    void deleteByProjectId(String projectId);
}