package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectReadCache {
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final JsonMapper json;

    public <T> List<T> read(
            String projectId,
            String view,
            Class<T[]> arrayType,
            Supplier<List<T>> load
    ) {
        String prefix = projectPrefix(projectId);
        String cacheKey;

        try {
            String revision = redis.opsForValue().get(prefix + "revision");
            cacheKey = prefix + view + ":" + (revision == null ? "0" : revision);
            String value = redis.opsForValue().get(cacheKey);

            if (value != null) {
                T[] items = json.readValue(value, arrayType);
                if (items != null) {
                    return List.of(items);
                }
            }
        } catch (DataAccessException | JacksonException exception) {
            log.warn("Could not read cache for project {} and view {}", projectId, view, exception);
            return load.get();
        }

        List<T> items = load.get();
        try {
            redis.opsForValue().set(cacheKey, json.writeValueAsString(items), TTL);
        } catch (DataAccessException | JacksonException exception) {
            log.warn("Could not write cache for project {} and view {}", projectId, view, exception);
        }
        return items;
    }

    public void invalidate(String projectId) {
        try {
            redis.opsForValue().increment(projectPrefix(projectId) + "revision");
        } catch (DataAccessException exception) {
            log.warn("Could not invalidate cache for project {}", projectId, exception);
        }
    }

    private String projectPrefix(String projectId) {
        return "tracker:cache:project:" + projectId + ":";
    }
}