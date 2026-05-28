Here is a comprehensive, production-ready `README.md` for your rate limiter codebase. It documents the design patterns used, explains how each algorithm behaves (with pros and cons), and provides clear instructions on how to use and test the code.

---

# Rate Limiter Using Strategy Design Pattern

A lightweight, thread-safe Java implementation of industry-standard API rate-limiting algorithms. This project demonstrates three distinct behavioral patterns for controlling traffic flow and protecting system resources from abuse or cascading failures.

---

## 🏗️ Architecture & Core Components

The system is designed around the **Strategy Pattern**. The core rate-limiting logic is decoupled from the execution lifecycle, allowing applications to swap algorithms seamlessly depending on the traffic profile required.

* **`RateLimiter` (Interface):** Defines the abstraction for throttling strategies.
* **`RateLimitResult` (Record):** An immutable Value Object holding the evaluation metrics (`allowed`, `remainingTokens`, `retryAfterMillis`, `reason`).
* **Concrete Implementations:** * `TokenBucketRateLimiter`
* `FixedWindowRateLimiter`
* `SlidingWindowRateLimiter`



---

## 📈 Supported Algorithms

### 1. Token Bucket

* **How it works:** A bucket is filled with a maximum capacity of tokens. Every incoming request consumes exactly one token. Tokens regenerate at a steady, fixed rate over time.
* **Best for:** Applications that need to support **bursty traffic**. If a client hasn't made requests in a while, they can use their accumulated capacity all at once.
* **Pros:** Smooths out long-term traffic distribution while allowing immediate short-term flexibility.

### 2. Fixed Window

* **How it works:** Time is mapped into static, non-shifting windows (e.g., specific 1-minute blocks). A simple counter tracks requests within that block and resets to zero the moment the clock crosses into the next window boundary.
* **Best for:** Low-memory footprints or simple threshold enforcement where absolute chronological precision is secondary to performance.
* **Cons:** Susceptible to the **"boundary burst" vulnerability**, where a client can double their allowed limit by flooding the system at the very end of one window and the very beginning of the next.

### 3. Sliding Window Log

* **How it works:** Tracks the precise epoch millisecond timestamp of every single successful request in a double-ended queue (`Deque`). Every new request dynamically evicts old timestamps outside the lookback range before validating capacity.
* **Best for:** Strict security or compliance use cases where boundary gaming cannot be tolerated.
* **Pros:** Completely eliminates the boundary burst problem; highly accurate.
* **Cons:** High memory usage under heavy volume, as every single transaction must store a timestamp in memory.

---

## 🛠️ Usage Examples

All implementations use a **lazy-refill/lazy-eviction strategy** calculated at the time of the request, eliminating the need for expensive background cron threads.

### Initializing a Limiter

```java
// Allow bursts up to 5 requests, refilling at 2 tokens per second
RateLimiter tokenBucket = new TokenBucketRateLimiter(5, 2);

// Allow a hard limit of 100 requests per absolute 60-second block
RateLimiter fixedWindow = new FixedWindowRateLimiter(100, 60);

// Allow 20 requests over any moving 10-second rolling window
RateLimiter slidingWindow = new SlidingWindowRateLimiter(20, 10);

```

---

## 🧵 Thread Safety & Concurrency

This design uses **localized synchronization locks** to ensure thread safety without creating global system bottlenecks:

1. Clients are stored inside a thread-safe `ConcurrentHashMap`.
2. Mutations to an individual client's state are scoped tightly inside a `synchronized(clientState)` block on that specific client's data structure reference.
3. **Performance Impact:** High concurrency from `User-A` will never block or introduce latency to `User-B`, making this suitable for multi-tenant microservices.

---

## 🧪 Verification & Testing

A verification runner is included in `RateLimiterTest.java`. It simulates rapid bursts, time delays for recovery validation, multi-tenant isolation, and explicit multi-threaded contention.

To execute the test suite:

```bash
javac RateLimiter.java RateLimitResult.java TokenBucketRateLimiter.java FixedWindowRateLimiter.java SlidingWindowRateLimiter.java RateLimiterTest.java
java RateLimiterTest

```

### Sample Verification Output

```text
========== TOKEN BUCKET ==========
-- Burst: fire 7 requests immediately (expect 5 allowed, 2 blocked) --
Request 1: allowed=true remaining=4 retryAfter=0ms
Request 2: allowed=true remaining=3 retryAfter=0ms
Request 3: allowed=true remaining=2 retryAfter=0ms
Request 4: allowed=true remaining=1 retryAfter=0ms
Request 5: allowed=true remaining=0 retryAfter=0ms
Request 6: allowed=false remaining=0 retryAfter=1000ms
Request 7: allowed=false remaining=0 retryAfter=1000ms

-- Wait 2 seconds for refill, then fire 3 more --
After refill request 1: allowed=true remaining=3
After refill request 2: allowed=true remaining=2
After refill request 3: allowed=true remaining=1

-- Different client key should have its own bucket --
user-2 first request: allowed=true remaining=4

========== FIXED WINDOW ==========
-- Fire 7 requests (expect 5 allowed, 2 blocked) --
Request 1: allowed=true remaining=4 retryAfter=0ms
Request 2: allowed=true remaining=3 retryAfter=0ms
Request 3: allowed=true remaining=2 retryAfter=0ms
Request 4: allowed=true remaining=1 retryAfter=0ms
Request 5: allowed=true remaining=0 retryAfter=0ms
Request 6: allowed=false remaining=0 retryAfter=2000ms
Request 7: allowed=false remaining=0 retryAfter=2000ms

-- Wait for next window (3 sec), then fire 3 --
New window request 1: allowed=true remaining=4
New window request 2: allowed=true remaining=3
New window request 3: allowed=true remaining=2

========== SLIDING WINDOW ==========
-- Fire 5 requests (all allowed) --
Request 1: allowed=true remaining=4
Request 2: allowed=true remaining=3
Request 3: allowed=true remaining=2
Request 4: allowed=true remaining=1
Request 5: allowed=true remaining=0

-- Fire 2 more immediately (expect blocked) --
Excess request 1: allowed=false retryAfter=2999ms
Excess request 2: allowed=false retryAfter=2999ms

-- Wait 3 sec for window to slide, fire 3 more (expect allowed) --
After slide request 1: allowed=true remaining=4
After slide request 2: allowed=true remaining=3
After slide request 3: allowed=true remaining=2

-- Concurrency test: 10 threads hammering same key simultaneously --
Concurrency result — allowed=5 blocked=5 (total=10)

```
