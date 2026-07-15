package ru.workbit.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.workbit.exception.TooManyRequestsException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterService {
    private static final int CLEANUP_THRESHOLD = 10_000;

    @Value("${app.security.rate-limit.limit}")
    private int limit;

    @Value("${app.security.rate-limit.window}")
    private Duration window;

    private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();

    private record Window(long startedAt, long windowMillis, AtomicInteger count) {
    }

    public void check(String key) {
        check(key, limit, window);
    }

    public void check(String key, int limit, Duration window) {
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
