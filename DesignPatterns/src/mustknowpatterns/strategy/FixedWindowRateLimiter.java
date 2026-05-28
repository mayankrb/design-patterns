package mustknowpatterns.strategy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * A memory-efficient, bucketed {@link RateLimiter} implementation utilizing the Fixed Window algorithm.
 * This algorithm divides time into static, absolute windows (e.g., [0-3s], [3-6s]).
 * While highly performant and consuming minimal memory per user, it is susceptible to
 * traffic bursts up to twice the configured limit near window boundary edges.
 */
public class FixedWindowRateLimiter implements RateLimiter {

    private final long maxRequests;
    private final long windowSizeSeconds;

    /**
     * Map storing the tracking state for each client.
     * Key: Unique client identifier.
     * Value: A primitive array representation of the state to optimize memory footprint:
     * window[0] -> Current request count within the active window.
     * window[1] -> The unique identifier (epoch step) of the active window.
     */
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    /**
     * Constructs a fixed window rate limiter.
     *
     * @param maxRequests       The maximum number of requests allowed in a single window.
     * @param windowSizeSeconds The fixed duration of each window in seconds.
     */
    public FixedWindowRateLimiter(long maxRequests, long windowSizeSeconds) {
        this.maxRequests = maxRequests;
        this.windowSizeSeconds = windowSizeSeconds;
    }

    @Override
    public RateLimitResult tryConsume(String clientKey) {
        // Convert current epoch milliseconds to seconds for simpler window mapping
        long now = System.currentTimeMillis() / 1000;

        // Determine the absolute window block identifier.
        // E.g., if window size is 3 seconds, seconds 0, 1, 2 = Window 0; seconds 3, 4, 5 = Window 1.
        long currentWindow = now / windowSizeSeconds;

        // Atomically initialize the state array for new client keys
        windows.putIfAbsent(clientKey, new long[]{0, currentWindow});
        long[] window = windows.get(clientKey);

        // Synchronize on the specific client's array reference to prevent race conditions
        // when reading/mutating counts under high concurrency.
        synchronized (window) {

            // 1. WINDOW OVERFLOW CHECK
            // If the current system time belongs to a newer window block than what is saved,
            // reset the hit counter back to 0 and advance the window marker.
            if (window[1] != currentWindow) {
                window[0] = 0;         // Reset the traffic counter
                window[1] = currentWindow; // Update to the new window index
            }

            // Increment the counter for the current request
            window[0]++;

            // 2. CAPACITY EVALUATION
            boolean allowed = window[0] <= maxRequests;
            long remaining = Math.max(0, maxRequests - window[0]);

            // 3. RETRY CALCULATION
            // If blocked, calculate the exact millisecond delay until the current window expires
            // and the next window starts.
            // Formula: (Next Window Index * Window Duration in Millis) - Current Time in Millis
            long retryAfter = allowed ? 0 : ((currentWindow + 1) * windowSizeSeconds * 1000) - (now * 1000);

            return new RateLimitResult(
                    allowed,
                    remaining,
                    retryAfter,
                    allowed ? "OK" : "Fixed window limit reached"
            );
        }
    }
}