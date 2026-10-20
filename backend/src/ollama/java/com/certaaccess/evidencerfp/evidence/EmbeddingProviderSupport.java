package com.certaaccess.evidencerfp.evidence;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class EmbeddingProviderSupport {
    private static final int MAX_RETRIES = 2;

    private EmbeddingProviderSupport() { }

    static <T> T call(Callable<T> providerCall, Duration timeout, int maxRetries) {
        int retryBudget = Math.min(MAX_RETRIES, Math.max(0, maxRetries));
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt <= retryBudget; attempt++) {
            FutureTask<T> future = new FutureTask<>(providerCall);
            Thread worker = Thread.ofVirtual().name("embedding-provider-").start(future);
            try {
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                future.cancel(true);
                lastFailure = new EvidenceProviderException("EMBEDDING_PROVIDER_TIMEOUT",
                        "Der Embedding-Aufruf hat das Zeitlimit überschritten.", exception);
            } catch (InterruptedException exception) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new EvidenceProviderException("EMBEDDING_PROVIDER_INTERRUPTED",
                        "Der Embedding-Aufruf wurde unterbrochen.", exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                lastFailure = cause instanceof RuntimeException runtime
                        ? runtime : new RuntimeException(cause);
                if (!retryable(lastFailure)) {
                    throw new EvidenceProviderException("EMBEDDING_PROVIDER_FAILURE",
                            "Der Embedding-Provider ist fehlgeschlagen.", lastFailure);
                }
            } finally {
                if (!future.isDone()) future.cancel(true);
                if (future.isCancelled()) worker.interrupt();
            }
            if (attempt < retryBudget) backoff(attempt);
        }
        throw new EvidenceProviderException("EMBEDDING_RETRIES_EXHAUSTED",
                "Der Embedding-Provider war nach mehreren Versuchen nicht erreichbar.", lastFailure);
    }

    private static boolean retryable(RuntimeException exception) {
        String name = exception.getClass().getName().toLowerCase(Locale.ROOT);
        String message = String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
        return name.contains("resourceaccess") || name.contains("timeout") || name.contains("connect")
                || message.contains("429") || message.matches(".*\\b5\\d\\d\\b.*")
                || message.contains("timeout") || message.contains("timed out")
                || message.contains("connection reset") || message.contains("connection refused");
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(200L * (attempt + 1));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EvidenceProviderException("EMBEDDING_PROVIDER_INTERRUPTED",
                    "Der Embedding-Aufruf wurde unterbrochen.", exception);
        }
    }
}
