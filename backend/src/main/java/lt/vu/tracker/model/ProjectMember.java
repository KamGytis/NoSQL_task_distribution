package lt.vu.tracker.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lt.vu.tracker.model.enums.ProjectRole;
import org.springframework.data.mongodb.core.index.Indexed;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ProjectMember {
    @Indexed
    private String userId;
    private ProjectRole role;
}