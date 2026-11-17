package lt.vu.tracker.dto.response;

import lt.vu.tracker.model.User;

public record UserSummary(String id, String firstName, String lastName, String email) {
    public static UserSummary from(User u) {
        return u == null ? null : new UserSummary(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail());
    }
}