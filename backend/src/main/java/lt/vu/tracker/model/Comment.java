package lt.vu.tracker.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("comments")
@CompoundIndex(name = "issue_created", def = "{'issueId': 1, 'createdAt': 1}")
@Getter @Setter @NoArgsConstructor
public class Comment {
    @Id
    private String id;
    private String issueId;
    private String issueKey;
    @Indexed
    private String projectId;
    private String authorId;
    private String text;
    private List<String> mentions = new ArrayList<>();   // user ids
    private Instant createdAt;
    private Instant editedAt;
}