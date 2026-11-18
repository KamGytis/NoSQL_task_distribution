package lt.vu.tracker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static lt.vu.tracker.TestData.assertStatus;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SprintLockTest {
    private static final String KEY = "tracker:sprint-lock:p1";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final SprintLock lock = new SprintLock(redis);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void skipsTheActionWhenAnotherRequestOwnsTheLock() {
        Supplier<?> action = mock(Supplier.class);
        when(values.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(false);

        assertStatus(() -> lock.withLock("p1", action), 409);
        verifyNoInteractions(action);
    }

    @Test
    void releasesWithTheSameTokenEvenWhenActionThrows() {
        when(values.setIfAbsent(eq(KEY), anyString(), eq(TTL))).thenReturn(true);
        var failure = new IllegalStateException("test failure");

        assertThatThrownBy(() -> lock.withLock("p1", () -> { throw failure; })).isSameAs(failure);

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq(KEY), token.capture(), eq(TTL));
        verify(redis).execute(ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(KEY)), eq(token.getValue()));
    }
}