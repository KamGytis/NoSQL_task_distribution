package lt.vu.tracker.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lt.vu.tracker.model.enums.NotificationType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("notifications")
@CompoundIndex(name = "user_read_created", def = "{'userId': 1, 'read': 1, 'createdAt': -1}")
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id
    private String id;
    private String userId;            // the recipient
    private NotificationType type;
    private String actorId;           // who caused it
    private String projectId;
    private String issueKey;
    private String commentId;         // only for MENTION
    private boolean read;
    @Indexed(expireAfterSeconds = 60 * 60 * 24 * 90)   // expires after 90 days
    private Instant createdAt;
}