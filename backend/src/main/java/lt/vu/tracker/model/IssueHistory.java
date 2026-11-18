package lt.vu.tracker.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lt.vu.tracker.model.enums.HistoryAction;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("issue_history")
@CompoundIndexes({
        @CompoundIndex(name = "issue_time", def = "{'issueId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "project_time", def = "{'projectId': 1, 'createdAt': -1}")
})
@Getter @Setter @NoArgsConstructor
public class IssueHistory {
    @Id
    private String id;
    private String issueId;
    private String issueKey;
    private String projectId;
    private String actorId;
    private HistoryAction action;
    private List<FieldChange> changes = new ArrayList<>();
    private Instant createdAt;
}