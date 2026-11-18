package lt.vu.tracker.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lt.vu.tracker.model.enums.IssueStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("issues")
@Getter
@Setter
@NoArgsConstructor
public class Issue {
    @Id
    private String id;

    @Indexed(unique = true)
    private String key;

    @Indexed
    private String projectId;
    
    @Indexed
    private String assigneeId;

    private String title;
    private String description;
    private IssueStatus status;
    private String reporterId;
    private Instant createdAt;

    @Version
    private Long version;
}
