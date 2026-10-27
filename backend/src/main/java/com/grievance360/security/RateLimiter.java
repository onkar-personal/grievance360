package com.grievance360.security;

import com.grievance360.common.ApiException;
import com.grievance360.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter for authentication endpoints. Per-instance only; for multiple backend
 * replicas, replace with a shared store (e.g. Redis) or rate-limit at the gateway.
 */
@Component
public class RateLimiter {
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowMillis;
    private final Clock clock;

    public RateLimiter(AppProperties props) {
        this.limit = props.auth().loginAttemptsPerWindow();
        this.windowMillis = props.auth().windowSeconds() * 1000;
        this.clock = Clock.systemUTC();
    }

    public void check(String key) {
        long now = clock.millis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMillis) q.pollFirst();
            if (q.size() >= limit) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                        "Too many attempts. Please wait a few minutes and try again.");
            }
            q.addLast(now);
        }
        if (hits.size() > 50_000) hits.clear(); // crude memory bound
    }

    public void reset(String key) {
        hits.remove(key);
    }
}
