package lt.vu.tracker;

import lt.vu.tracker.model.Project;
import lt.vu.tracker.model.ProjectMember;
import lt.vu.tracker.model.User;
import lt.vu.tracker.model.enums.ProjectRole;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class TestData {
    private TestData() {}

    public static User user(String id, String email) {
        User u = new User();
        u.setId(id);
        u.setFirstName("Test");
        u.setLastName("User");
        u.setEmail(email);
        u.setBirthYear(2000);
        return u;
    }

    public static ProjectMember member(String userId, ProjectRole role) {
        return new ProjectMember(userId, role);
    }

    public static Project project(String id, ProjectMember... members) {
        Project p = new Project();
        p.setId(id);
        p.setKey("DEMO");
        p.setName("Demo");
        p.setMembers(new ArrayList<>(List.of(members)));
        return p;
    }

    public static void assertStatus(ThrowingCallable call, int expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(expected));
    }
}