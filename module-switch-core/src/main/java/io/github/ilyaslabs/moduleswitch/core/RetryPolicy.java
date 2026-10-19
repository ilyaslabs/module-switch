package io.github.ilyaslabs.moduleswitch.core;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * When and how long to wait before retrying a remote module call.
 * <p>
 * By default only failures where the request certainly never ran are retried. Methods marked
 * {@code @Idempotent} are also retried when the outcome is unknown.
 */
public final class RetryPolicy {

    /** What went wrong with one attempt. */
    public enum Failure {
        /** Connection refused, unknown host, connect timeout, or the server said it is not ready. */
        NEVER_SENT,
        /** Read timeout, broken connection, or 502/503/504: the method may or may not have run. */
        OUTCOME_UNKNOWN,
        /** Anything else, such as a ModuleException from the other module. */
        FINAL
    }

    private final int maxAttempts;

    private final Duration initialBackoff;

    public RetryPolicy(int maxAttempts, Duration initialBackoff) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoff = (initialBackoff != null) ? initialBackoff : Duration.ZERO;
    }

    public int maxAttempts() {
        return this.maxAttempts;
    }

    /** Whether to try again after {@code attempt} (1-based) failed this way. */
    public boolean shouldRetry(Failure failure, boolean idempotent, int attempt) {
        if (attempt >= this.maxAttempts) {
            return false;
        }
        return switch (failure) {
            case NEVER_SENT -> true;
            case OUTCOME_UNKNOWN -> idempotent;
            case FINAL -> false;
        };
    }

    /**
     * The wait before retry number {@code retry} (1-based): the initial backoff doubled each time,
     * scaled by a random factor between 0.5 and 1.5 so callers don't retry in lockstep.
     */
    public Duration backoff(int retry) {
        long base = this.initialBackoff.toMillis() << Math.min(retry - 1, 20);
        double jitter = 0.5 + ThreadLocalRandom.current().nextDouble();
        return Duration.ofMillis(Math.round(base * jitter));
    }
}
