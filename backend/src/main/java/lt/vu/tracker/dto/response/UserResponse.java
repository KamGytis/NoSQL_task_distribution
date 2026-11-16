package lt.vu.tracker.dto.response;

import lt.vu.tracker.model.User;

import java.time.Instant;

public record UserResponse(String id, String firstName, String lastName,
                           String email, Integer birthYear, Instant createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getFirstName(), u.getLastName(),
                u.getEmail(), u.getBirthYear(), u.getCreatedAt());
    }
}