package mustknowpatterns.strategy;

/**
 * Test runner to validate the behavior of three common rate-limiting algorithms:
 * Token Bucket, Fixed Window, and Sliding Window.
 * * Each test verifies capacity limits, behavior under bursts, recovery/refill
 * mechanics over time, and client isolation.
 */
public class RateLimiterTest {

    public static void main(String[] args) throws InterruptedException {
        testTokenBucket();
        testFixedWindow();
        testSlidingWindow();
    }

    // ─── Token Bucket ────────────────────────────────────────────────────────

    /**
     * Tests the Token Bucket algorithm.
     * This algorithm allows for bursts of traffic up to the max capacity
     * and steadily refills tokens at a constant background rate.
     */
    static void testTokenBucket() throws InterruptedException {
        System.out.println("\n========== TOKEN BUCKET ==========");
        // Configuration: Bucket holds a max of 5 tokens, refills at 2 tokens per second.
        RateLimiter limiter = new TokenBucketRateLimiter(5, 2);

        System.out.println("-- Burst: fire 7 requests immediately (expect 5 allowed, 2 blocked) --");
        for (int i = 1; i <= 7; i++) {
            RateLimitResult result = limiter.tryConsume("user-1");
            // The first 5 should exhaust the bucket; the next 2 should be rejected with a retry backoff.
            System.out.printf("Request %d: allowed=%b remaining=%d retryAfter=%dms%n",
                    i, result.allowed(), result.remainingTokens(), result.retryAfterMillis());
        }

        System.out.println("\n-- Wait 2 seconds for refill, then fire 3 more --");
        // Waiting 2 seconds should regenerate ~4 tokens (2 tokens/sec * 2 sec).
        Thread.sleep(2000);
        for (int i = 1; i <= 3; i++) {
            RateLimitResult result = limiter.tryConsume("user-1");
            // All 3 requests should succeed since the bucket has refilled.
            System.out.printf("After refill request %d: allowed=%b remaining=%d%n",
                    i, result.allowed(), result.remainingTokens());
        }

        System.out.println("\n-- Different client key should have its own bucket --");
        // Verify multi-tenant isolation: user-2 should have a completely fresh, full bucket.
        RateLimitResult result = limiter.tryConsume("user-2");
        System.out.printf("user-2 first request: allowed=%b remaining=%d%n",
                result.allowed(), result.remainingTokens());
    }

    // ─── Fixed Window ────────────────────────────────────────────────────────

    /**
     * Tests the Fixed Window algorithm.
     * This algorithm divides time into static windows (e.g., [0-3s], [3-6s]).
     * It tracks a simple counter per window and resets it entirely at the window boundary.
     */
    static void testFixedWindow() throws InterruptedException {
        System.out.println("\n========== FIXED WINDOW ==========");
        // Configuration: Max 5 requests per distinct 3-second window.
        RateLimiter limiter = new FixedWindowRateLimiter(5, 3);

        System.out.println("-- Fire 7 requests (expect 5 allowed, 2 blocked) --");
        for (int i = 1; i <= 7; i++) {
            RateLimitResult result = limiter.tryConsume("user-1");
            // The first 5 requests fill the current fixed window; requests 6 and 7 will be blocked.
            System.out.printf("Request %d: allowed=%b remaining=%d retryAfter=%dms%n",
                    i, result.allowed(), result.remainingTokens(), result.retryAfterMillis());
        }

        System.out.println("\n-- Wait for next window (3 sec), then fire 3 --");
        // Sleep guarantees the clock crosses into the next absolute 3-second window interval.
        Thread.sleep(3000);
        for (int i = 1; i <= 3; i++) {
            RateLimitResult result = limiter.tryConsume("user-1");
            // Counter resets to 0 for the new window, so these 3 requests are easily allowed.
            System.out.printf("New window request %d: allowed=%b remaining=%d%n",
                    i, result.allowed(), result.remainingTokens());
        }
    }

    // ─── Sliding Window ──────────────────────────────────────────────────────

    /**
     * Tests the Sliding Window algorithm.
     * Unlike Fixed Window, this tracks timestamps relative to the exact moment of the request,
     * smoothing out artificial traffic spikes that happen right at the edge of fixed boundaries.
     */
    static void testSlidingWindow() throws InterruptedException {
        System.out.println("\n========== SLIDING WINDOW ==========");
        // Configuration: Max 5 requests allowed in any moving 3-second window.
        RateLimiter limiter = new SlidingWindowRateLimiter(5, 3);

        System.out.println("-- Fire 5 requests (all allowed) --");
        for (int i = 1; i <= 5; i++) {
            RateLimitResult result = limiter.tryConsume("user-1");
            // Fills up the capacity for the current immediate time frame.
            System.out.printf("Request %d: allowed=%b remaining=%d%n",
                    i, result.allowed(), result.remainingTokens());
        }

        System.out.println("\n-- Fire 2 more immediately (expect blocked) --");
        for (int i = 1; i <= 2; i++) {
            RateLimitResult result = limiter.tryConsume("user-1");
            // Rejected because the rolling 3-second lookback window still counts the 5 requests just made.
            System.out.printf("Excess request %d: allowed=%b retryAfter=%dms%n",
                    i, result.allowed(), result.retryAfterMillis());
        }

        System.out.println("\n-- Wait 3 sec for window to slide, fire 3 more (expect allowed) --");
        // Waiting 3 seconds pushes the initial 5 requests out of the active lookback window.
        Thread.sleep(3000);
        for (int i = 1; i <= 3; i++) {
            RateLimitResult result = limiter.tryConsume("user-1");
            System.out.printf("After slide request %d: allowed=%b remaining=%d%n",
                    i, result.allowed(), result.remainingTokens());
        }

        System.out.println("\n-- Concurrency test: 10 threads hammering same key simultaneously --");
        // Validates that the implementation handles multi-threaded access without race conditions.
        testConcurrency(limiter);
    }

    // ─── Concurrency ─────────────────────────────────────────────────────────

    /**
     * Spawns multiple concurrent threads to stress-test the rate limiter's
     * thread safety and ensure accurate transaction tracking under heavy contention.
     */
    static void testConcurrency(RateLimiter limiter) throws InterruptedException {
        int threadCount = 10;
        // Using arrays as mutable wrappers for counts inside lambda expressions
        int[] allowed = {0};
        int[] blocked = {0};

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                RateLimitResult result = limiter.tryConsume("concurrent-user");
                // Synchronize updates to ensure thread-safe increments of our local test counters
                synchronized (allowed) {
                    if (result.allowed()) allowed[0]++;
                    else blocked[0]++;
                }
            });
        }

        // Start all threads simultaneously to trigger high contention
        for (Thread t : threads) t.start();

        // Wait for all threads to finish execution before reporting results
        for (Thread t : threads) t.join();

        System.out.printf("Concurrency result — allowed=%d blocked=%d (total=%d)%n",
                allowed[0], blocked[0], allowed[0] + blocked[0]);
    }
}
