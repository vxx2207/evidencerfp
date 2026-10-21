package com.certaaccess.evidencerfp.evidence;

import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingProviderSupportTest {
    @Test
    void zeroSpringAiRetriesMeansOneRealProviderCall() {
        SpringAiRetryProperties properties = new SpringAiRetryProperties();
        properties.setMaxAttempts(0);
        var retryTemplate = new SpringAiRetryAutoConfiguration().retryTemplate(properties);
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retryTemplate.invoke(() -> {
            calls.incrementAndGet();
            throw new TransientAiException("HTTP 503");
        }));

        assertThat(calls).hasValue(1);
    }

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
    void capsTheConfiguredNetworkRetryBudgetAtTwoRetries() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> EmbeddingProviderSupport.call(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("HTTP 503");
        }, Duration.ofSeconds(1), 99))
                .isInstanceOfSatisfying(EvidenceProviderException.class,
                        exception -> assertThat(exception.code()).isEqualTo("EMBEDDING_RETRIES_EXHAUSTED"));
        assertThat(calls).hasValue(3);
    }
}
