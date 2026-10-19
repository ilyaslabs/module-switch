package io.github.ilyaslabs.moduleswitch.core;

import java.time.Duration;

import io.github.ilyaslabs.moduleswitch.core.RetryPolicy.Failure;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(100));

    @Test
    void neverSentIsRetriedForEveryMethod() {
        assertThat(this.policy.shouldRetry(Failure.NEVER_SENT, false, 1)).isTrue();
        assertThat(this.policy.shouldRetry(Failure.NEVER_SENT, true, 2)).isTrue();
    }

    @Test
    void unknownOutcomeIsRetriedOnlyForIdempotentMethods() {
        assertThat(this.policy.shouldRetry(Failure.OUTCOME_UNKNOWN, false, 1)).isFalse();
        assertThat(this.policy.shouldRetry(Failure.OUTCOME_UNKNOWN, true, 1)).isTrue();
    }

    @Test
    void finalFailuresAreNeverRetried() {
        assertThat(this.policy.shouldRetry(Failure.FINAL, true, 1)).isFalse();
    }

    @Test
    void stopsAtMaxAttempts() {
        assertThat(this.policy.shouldRetry(Failure.NEVER_SENT, true, 3)).isFalse();
        assertThat(new RetryPolicy(1, Duration.ZERO).shouldRetry(Failure.NEVER_SENT, true, 1)).isFalse();
        assertThat(new RetryPolicy(0, null).maxAttempts()).isEqualTo(1);
    }

    @RepeatedTest(20)
    void backoffDoublesWithJitter() {
        assertThat(this.policy.backoff(1)).isBetween(Duration.ofMillis(50), Duration.ofMillis(150));
        assertThat(this.policy.backoff(2)).isBetween(Duration.ofMillis(100), Duration.ofMillis(300));
        assertThat(this.policy.backoff(3)).isBetween(Duration.ofMillis(200), Duration.ofMillis(600));
    }

    @Test
    void backoffDoesNotOverflow() {
        assertThat(this.policy.backoff(500)).isPositive();
    }
}
