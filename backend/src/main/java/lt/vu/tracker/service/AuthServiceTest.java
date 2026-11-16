package lt.vu.tracker.service;

import lt.vu.tracker.dto.request.RegisterRequest;
import lt.vu.tracker.dto.response.UserResponse;
import lt.vu.tracker.model.User;
import lt.vu.tracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Year;
import java.util.Optional;

import static lt.vu.tracker.TestData.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, passwordEncoder);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");
    }

    @Test
    void registerNormalisesEmailAndHashesPassword() {
        UserResponse res = service.register(
                new RegisterRequest(" Jonas ", " Jonaitis ", 2000, "  Jonas@Test.LT ", "password123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("jonas@test.lt");
        assertThat(saved.getFirstName()).isEqualTo("Jonas");
        assertThat(saved.getLastName()).isEqualTo("Jonaitis");
        assertThat(saved.getPasswordHash()).isEqualTo("HASH").isNotEqualTo("password123");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(res.email()).isEqualTo("jonas@test.lt");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("a@test.lt")).thenReturn(true);
        assertStatus(() -> service.register(
                new RegisterRequest("A", "B", 2000, "a@test.lt", "password123")), 409);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsBirthYearInTheFuture() {
        int future = Year.now().getValue() + 1;
        assertStatus(() -> service.register(
                new RegisterRequest("A", "B", future, "a@test.lt", "password123")), 400);
    }

    @Test
    void getByEmailThrows404ForUnknownUser() {
        when(userRepository.findByEmail("x@test.lt")).thenReturn(Optional.empty());
        assertStatus(() -> service.getByEmail("x@test.lt"), 404);
    }
}