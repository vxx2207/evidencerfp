package com.certaaccess.evidencerfp.evidence;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingProviderSupportTest {
    @Test
    void stopsAProviderAttemptAtTheConfiguredTimeout() {
        long started = System.nanoTime();

        assertThatThrownBy(() -> EmbeddingProviderSupport.call(() -> {
            Thread.sleep(5_000);
            return "too late";
        }, Duration.ofMillis(25), 0))
                .isInstanceOfSatisfying(EvidenceProviderException.class,
                        exception -> assertThat(exception.code()).isEqualTo("EMBEDDING_RETRIES_EXHAUSTED"));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void retriesOnlyUpToTheConfiguredNetworkRetryBudget() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> EmbeddingProviderSupport.call(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("HTTP 503");
        }, Duration.ofSeconds(1), 2))
                .isInstanceOfSatisfying(EvidenceProviderException.class,
                        exception -> assertThat(exception.code()).isEqualTo("EMBEDDING_RETRIES_EXHAUSTED"));
        assertThat(calls).hasValue(3);
    }
}
