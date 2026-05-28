package mustknowpatterns.strategy;


/**
 * Defines the contract for a rate-limiting strategy.
 * Implementations are responsible for tracking request counts, managing time windows
 * or token pools, and ensuring thread-safe evaluation per client key.
 */
public interface RateLimiter {

    /**
     * Attempts to consume a single permit/token for a given client.
     *
     * @param clientKey A unique identifier for the client (e.g., an IP address, API key, or user ID).
     * @return A {@link RateLimitResult} indicating whether the request is permitted and metadata for the client.
     */
    RateLimitResult tryConsume(String clientKey);
}