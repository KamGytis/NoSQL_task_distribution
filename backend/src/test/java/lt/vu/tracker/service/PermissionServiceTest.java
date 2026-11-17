package lt.vu.tracker.service;

import lt.vu.tracker.model.Project;
import lt.vu.tracker.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static lt.vu.tracker.TestData.*;
import static lt.vu.tracker.model.enums.ProjectRole.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionServiceTest {
    private final ProjectRepository repo = mock(ProjectRepository.class);
    private final PermissionService permissions = new PermissionService(repo);
    private final Project project =
            project("p1", member("u1", ADMIN), member("u2", MEMBER), member("u3", VIEWER));

    @BeforeEach
    void setUp() {
        when(repo.findById("p1")).thenReturn(Optional.of(project));
    }

    @Test
    void returnsProjectWhenRoleIsHighEnough() {
        assertThat(permissions.requireRole("p1", "u1", MEMBER)).isSameAs(project);
        assertThat(permissions.requireRole("p1", "u2", MEMBER)).isSameAs(project);
    }

    @Test
    void forbidsTooLowRole() {
        assertStatus(() -> permissions.requireRole("p1", "u3", MEMBER), 403);
        assertStatus(() -> permissions.requireRole("p1", "u2", ADMIN), 403);
    }

    @Test
    void hidesProjectFromNonMembers() {
        assertStatus(() -> permissions.requireRole("p1", "stranger", VIEWER), 404);
    }

    @Test
    void missingProjectIs404() {
        when(repo.findById("nope")).thenReturn(Optional.empty());
        assertStatus(() -> permissions.requireRole("nope", "u1", VIEWER), 404);
    }

    @Test
    void roleOfReturnsTheMembersRole() {
        assertThat(permissions.roleOf(project, "stranger")).isEmpty();
        assertThat(permissions.roleOf(project, "u3")).contains(VIEWER);
    }
}