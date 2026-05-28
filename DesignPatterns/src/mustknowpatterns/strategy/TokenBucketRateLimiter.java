package mustknowpatterns.strategy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A highly resilient and burst-friendly {@link RateLimiter} implementation using the Token Bucket algorithm.
 * Tokens accumulate in a bucket up to a maximum capacity at a fixed refill rate.
 * Each request consumes exactly one token. This allows clients to handle traffic bursts up to
 * the bucket's maximum capacity while guaranteeing that long-term usage stays bounded.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final long capacity;
    private final long refillRatePerSecond;

    /**
     * Map tracking bucket states per client.
     * Key: Unique client identifier.
     * Value: A primitive array representing the state to preserve memory efficiency:
     * bucket[0] -> Current remaining tokens (tracked as a double to support fractional accumulation).
     * bucket[1] -> The timestamp (in epoch seconds) when the bucket was last refilled.
     */
    private final Map<String, double[]> buckets = new ConcurrentHashMap<>();

    /**
     * Constructs a token bucket rate limiter.
     *
     * @param capacity            The maximum number of tokens the bucket can hold (controls burst allowance).
     * @param refillRatePerSecond How many tokens are added back to the bucket each second.
     */
    public TokenBucketRateLimiter(long capacity, long refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    @Override
    public RateLimitResult tryConsume(String clientKey) {
        // Evaluate time in seconds to match the configuration's refill unit
        long now = System.currentTimeMillis() / 1000;

        // Atomically initialize a fully charged bucket for new clients
        buckets.putIfAbsent(clientKey, new double[]{capacity, now});
        double[] bucket = buckets.get(clientKey);

        // Synchronize on the specific client's array instance to ensure thread safety
        // across state calculations and token consumption under heavy load.
        synchronized (bucket) {

            // 1. LAZY REFILL CALCULATION
            // Calculate how much time has passed since this specific bucket was updated.
            double elapsed = now - bucket[1];

            // Increment the tokens based on elapsed time, capping it at the maximum capacity.
            bucket[0] = Math.min(capacity, bucket[0] + (elapsed * refillRatePerSecond));

            // Always bump the last refill timestamp to the current time snapshot.
            bucket[1] = now;

            // 2. TOKEN CONSUMPTION
            // If at least one complete token is available, allow the request.
            if (bucket[0] >= 1) {
                bucket[0]--; // Deduct one token

                return new RateLimitResult(true, (long) bucket[0], 0, "OK");
            }

            // 3. RETRY CALCULATION (If throttled)
            // Calculate how many milliseconds are required to regenerate at least 1 token.
            // Formula: 1.0 / refillRatePerSecond gives the generation time for 1 token in seconds.
            // Ceiled and converted to milliseconds.
            long retryAfter = (long) Math.ceil(1.0 / refillRatePerSecond) * 1000;

            return new RateLimitResult(false, 0, retryAfter, "Token bucket exhausted");
        }
    }
}