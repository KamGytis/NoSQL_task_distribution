package lt.vu.tracker.model.enums;

import org.junit.jupiter.api.Test;

import static lt.vu.tracker.model.enums.ProjectRole.*;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectRoleTest {
    @Test
    void rolesAreOrdered() {
        assertThat(ADMIN.atLeast(MEMBER)).isTrue();
        assertThat(MEMBER.atLeast(MEMBER)).isTrue();
        assertThat(VIEWER.atLeast(MEMBER)).isFalse();
        assertThat(MEMBER.atLeast(ADMIN)).isFalse();
    }
}