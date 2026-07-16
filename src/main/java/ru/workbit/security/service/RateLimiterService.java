package ru.workbit.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.workbit.exception.TooManyRequestsException;
import ru.workbit.security.config.RateLimitProperties;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class RateLimiterService {
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final RateLimitProperties properties;

    private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();

    private record Window(long startedAt, long windowMillis, AtomicInteger count) {
    }

    public void check(String key) {
        check(key, properties.limit(), properties.window());
    }

    public void check(String key, RateLimitProperties.Bucket bucket) {
        check(key, bucket.limit(), bucket.window());
    }

    private void check(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();

        if (buckets.size() > CLEANUP_THRESHOLD) {
            buckets.values().removeIf(w -> now - w.startedAt() >= w.windowMillis());
        }

        Window current = buckets.compute(key, (k, w) ->
                w == null || now - w.startedAt() >= w.windowMillis()
                        ? new Window(now, windowMillis, new AtomicInteger())
                        : w);

        if (current.count().incrementAndGet() > limit) {
            throw new TooManyRequestsException("Too many requests");
        }
    }
}
