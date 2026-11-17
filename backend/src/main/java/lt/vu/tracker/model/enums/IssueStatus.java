package lt.vu.tracker.model.enums;

import java.util.EnumSet;
import java.util.Set;

public enum IssueStatus {
    TODO("To do"),
    IN_PROGRESS("In progress"),
    DONE("Done");

    private final String label;

    IssueStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public Set<IssueStatus> nextStatuses() {
        return switch (this) {
            case TODO -> EnumSet.of(IN_PROGRESS);
            case IN_PROGRESS -> EnumSet.of(TODO, DONE);
            case DONE -> EnumSet.of(IN_PROGRESS);
        };
    }

    public boolean canMoveTo(IssueStatus target) {
        return nextStatuses().contains(target);
    }
}