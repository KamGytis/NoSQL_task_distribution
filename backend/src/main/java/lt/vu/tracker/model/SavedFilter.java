package lt.vu.tracker.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lt.vu.tracker.model.enums.IssueStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("saved_filters")
@CompoundIndex(
        name = "owner_created",
        def = "{'ownerId': 1, 'createdAt': -1}"
)
@Getter
@Setter
@NoArgsConstructor
public class SavedFilter {
    @Id
    private String id;

    private String ownerId;
    private String projectId;
    private String name;
    private IssueStatus status;
    private String assigneeId;
    private String titleContains;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;
}