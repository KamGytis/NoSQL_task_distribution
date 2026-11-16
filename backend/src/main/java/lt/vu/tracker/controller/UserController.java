package lt.vu.tracker.controller;

import lombok.RequiredArgsConstructor;
import lt.vu.tracker.dto.response.UserResponse;
import lt.vu.tracker.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final AuthService authService;

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.getByEmail(authentication.getName());
    }
}