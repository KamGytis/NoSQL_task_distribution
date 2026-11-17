package lt.vu.tracker.model.enums;

import org.junit.jupiter.api.Test;

import static lt.vu.tracker.model.enums.IssueStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class IssueStatusTest {
    @Test
    void todoCanOnlyStart() {
        assertThat(TODO.nextStatuses()).containsExactly(IN_PROGRESS);
        assertThat(TODO.canMoveTo(DONE)).isFalse();
    }

    @Test
    void inProgressCanGoBackOrFinish() {
        assertThat(IN_PROGRESS.nextStatuses()).containsExactly(TODO, DONE);
    }

    @Test
    void doneCanOnlyBeReopened() {
        assertThat(DONE.nextStatuses()).containsExactly(IN_PROGRESS);
        assertThat(DONE.canMoveTo(TODO)).isFalse();
    }

    @Test
    void noStatusMovesToItself() {
        for (IssueStatus status : values()) {
            assertThat(status.canMoveTo(status)).isFalse();
        }
    }

    @Test
    void everyStatusHasALabel() {
        for (IssueStatus status : values()) {
            assertThat(status.getLabel()).isNotBlank();
        }
    }
}