package lt.vu.tracker.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("projects")
@Getter @Setter @NoArgsConstructor
public class Project {
    @Id
    private String id;
    @Indexed(unique = true)
    private String key;
    private String name;
    private String description;
    private String leadId;
    private List<ProjectMember> members = new ArrayList<>();
    private Instant createdAt;
}