package lt.vu.tracker.model.enums;

public enum ProjectRole {
    VIEWER, MEMBER, ADMIN;

    public boolean atLeast(ProjectRole other) {
        return this.ordinal() >= other.ordinal();
    }
}