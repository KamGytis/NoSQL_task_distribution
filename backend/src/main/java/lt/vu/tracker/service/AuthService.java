package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.request.RegisterRequest;
import lt.vu.tracker.dto.response.UserResponse;
import lt.vu.tracker.model.User;
import lt.vu.tracker.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.Year;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (req.birthYear() > Year.now().getValue()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid birth year");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = new User();
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setEmail(email);
        user.setBirthYear(req.birthYear());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setCreatedAt(Instant.now());
        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse getByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}