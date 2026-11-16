package lt.vu.tracker.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lt.vu.tracker.model.enums.InvitationStatus;
import lt.vu.tracker.model.enums.ProjectRole;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("invitations")
@Getter @Setter @NoArgsConstructor
public class Invitation {
    @Id
    private String id;
    @Indexed
    private String projectId;
    private String projectName;       // denormalised
    @Indexed
    private String invitedUserId;
    private String invitedBy;
    private ProjectRole role;
    private InvitationStatus status;
    private Instant createdAt;
}