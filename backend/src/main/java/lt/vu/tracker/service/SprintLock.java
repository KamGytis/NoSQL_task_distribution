package lt.vu.tracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class SprintLock {
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final RedisScript<Long> RELEASE = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public <T> T withLock(String projectId, Supplier<T> action) {
        String key = "tracker:sprint-lock:" + projectId;
        String token = UUID.randomUUID().toString();

        Boolean acquired;
        try {
            acquired = redis.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Sprint lock unavailable", exception);
        }
        if (acquired == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Sprint lock unavailable");
        }
        if (!acquired) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another sprint operation is in progress");
        }

        try {
            return action.get();
        } finally {
            try {
                redis.execute(RELEASE, List.of(key), token);
            } catch (DataAccessException exception) {
                log.warn("Could not release sprint lock for project {}. It will expire.", projectId, exception);
            }
        }
    }
}
