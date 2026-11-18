package lt.vu.tracker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectReadCacheTest {
    private static final String PREFIX = "tracker:cache:project:p1:";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ProjectReadCache cache = new ProjectReadCache(redis, JsonMapper.builder().build());

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void storesLoadedResultForSixtySeconds() {
        var result = cache.read("p1", "issues", String[].class, () -> List.of("First"));

        assertThat(result).containsExactly("First");
        verify(values).set(PREFIX + "issues:0", "[\"First\"]", Duration.ofSeconds(60));
    }

    @Test
    void usesCachedResultWithoutCallingLoader() {
        when(values.get(PREFIX + "board:0")).thenReturn("[\"Cached\"]");

        var result = cache.read("p1", "board", String[].class, () -> {
            throw new AssertionError("Loader ran on a cache hit");
        });

        assertThat(result).containsExactly("Cached");
    }

    @Test
    void readsNewRevisionAfterInvalidation() {
        when(values.get(PREFIX + "revision")).thenReturn("0", "1");
        when(values.get(PREFIX + "backlog:0")).thenReturn("[\"Old\"]");

        assertThat(cache.read("p1", "backlog", String[].class, List::of))
                .containsExactly("Old");

        cache.invalidate("p1");

        assertThat(cache.read("p1", "backlog", String[].class, () -> List.of("New")))
                .containsExactly("New");
        verify(values).increment(PREFIX + "revision");
        verify(values).set(PREFIX + "backlog:1", "[\"New\"]", Duration.ofSeconds(60));
    }

    @Test
    void loadsResultWhenRedisIsUnavailable() {
        when(values.get(PREFIX + "revision"))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));

        assertThat(cache.read("p1", "issues", String[].class, () -> List.of("Mongo")))
                .containsExactly("Mongo");
    }
}