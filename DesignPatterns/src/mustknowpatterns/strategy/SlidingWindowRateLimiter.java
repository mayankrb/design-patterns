package mustknowpatterns.strategy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * A precise, sliding-window log implementation of a {@link RateLimiter}.
 * This implementation tracks individual request timestamps for each client to provide
 * a smooth rolling window lookback, eliminating the boundary "burst" vulnerabilities
 * of a fixed window algorithm.
 */
public class SlidingWindowRateLimiter implements RateLimiter {

    private final long maxRequests;
    private final long windowSizeMillis;

    /**
     * Map tracking request history per client.
     * Key: The unique client identification string.
     * Value: A thread-safe reference to a Double-Ended Queue (Deque) holding
     * epoch millisecond timestamps of successful requests.
     */
    private final Map<String, Deque<Long>> requestLogs = new ConcurrentHashMap<>();

    /**
     * Constructs a sliding window rate limiter.
     *
     * @param maxRequests       The maximum number of permitted operations allowed within the moving window.
     * @param windowSizeSeconds The duration of the moving lookback window in seconds.
     */
    public SlidingWindowRateLimiter(long maxRequests, long windowSizeSeconds) {
        this.maxRequests = maxRequests;
        this.windowSizeMillis = windowSizeSeconds * 1000; // Convert to millis for precise timestamp comparisons
    }

    @Override
    public RateLimitResult tryConsume(String clientKey) {
        long now = System.currentTimeMillis();
        // Calculate the absolute starting point of the current rolling window
        long windowStart = now - windowSizeMillis;

        // Atomically initialize an empty request history queue for new clients
        requestLogs.putIfAbsent(clientKey, new ArrayDeque<>());
        Deque<Long> log = requestLogs.get(clientKey);

        // Synchronize on the specific client's log to guarantee thread-safety
        // during concurrent read/write modifications of this client's history.
        synchronized (log) {

            // 1. EVICT OUTDATED TIMESTAMPS
            // Remove any request logs that occurred before the current rolling window boundary.
            while (!log.isEmpty() && log.peekFirst() <= windowStart) {
                log.pollFirst(); // Remove the oldest timestamp
            }

            // 2. EVALUATE CAPACITY
            // The request is permitted only if the total requests remaining in the window is strictly less than max capacity.
            boolean allowed = log.size() < maxRequests;

            if (allowed) {
                // Log the current request timestamp to include it in subsequent sliding window evaluations
                log.addLast(now);

                long remainingTokens = maxRequests - log.size();
                return new RateLimitResult(true, remainingTokens, 0, "OK");
            }

            // 3. CALCULATE BACKOFF (If throttled)
            // Identify when the oldest request in the current window will expire.
            // Formula: The timestamp of that oldest request + window lifespan = when its slot frees up.
            long oldestTimestamp = log.peekFirst();
            long retryAfter = oldestTimestamp + windowSizeMillis - now;

            // Edge-case safety: ensure we don't accidentally return a negative delay due to system clock micro-adjustments
            retryAfter = Math.max(0, retryAfter);

            return new RateLimitResult(false, 0, retryAfter, "Sliding window limit reached");
        }
    }
}
