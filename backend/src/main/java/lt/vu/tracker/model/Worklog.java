package lt.vu.tracker.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("worklogs")
@CompoundIndexes({
        @CompoundIndex(name = "issue_started", def = "{'issueId': 1, 'startedAt': -1}")
})
@Getter @Setter @NoArgsConstructor
public class Worklog {
    @Id
    private String id;
    private String issueId;
    private String issueKey;       // denormalised: issue keys never change
    @Indexed
    private String projectId;      // denormalised: lets edit/delete check roles from a worklog id
    private String userId;
    private int minutes;
    private Instant startedAt;
    private String note;
    private Instant createdAt;
    private Instant updatedAt;
}