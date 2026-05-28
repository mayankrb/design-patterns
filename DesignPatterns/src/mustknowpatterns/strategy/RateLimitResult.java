package mustknowpatterns.strategy;

/**
 * Represents the immutable outcome of a rate-limiting check.
 * Encapsulates all necessary metadata required by the application layer to make
 * routing decisions or construct appropriate HTTP headers (e.g., X-RateLimit-Remaining).
 *
 * @param allowed            {@code true} if the request falls within the allowed threshold and can proceed;
 * {@code false} if the request was throttled/blocked.
 * @param remainingTokens    The number of tokens or requests still permitted within the current
 * active window/bucket before subsequent requests will be blocked.
 * @param retryAfterMillis   If blocked (allowed is false), specifies the backoff duration in milliseconds
 * the client must wait before a retry can succeed. Returns 0 if allowed is true.
 * @param reason             A descriptive message explaining the outcome (e.g., "Rate limit exceeded",
 * "Token bucket exhausted", or "Success"). Useful for logging and debugging.
 */
public record RateLimitResult(
        boolean allowed,
        long remainingTokens,
        long retryAfterMillis,
        String reason
) {
}
