package lt.vu.tracker.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lt.vu.tracker.model.enums.SprintStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("sprints")
@CompoundIndex(name = "sprints_by_project", def = "{'projectId': 1, 'createdAt': -1}")
@CompoundIndex(name = "one_active_sprint_per_project", def = "{'projectId': 1}",
        unique = true, partialFilter = "{'status': 'ACTIVE'}")
@Getter
@Setter
@NoArgsConstructor
public class Sprint {
    @Id
    private String id;
    private String projectId;
    private String name;
    private SprintStatus status;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    @Version
    private Long version;
}
