package com.certaaccess.evidencerfp.evidence;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Führt nicht vertrauenswürdige Dokumentparser in einem kleinen, begrenzten Bulkhead aus.
 *
 * <p>Ein Timeout unterbricht den Parser bestmöglich. Die JVM kann Bibliothekscode, der einen
 * Interrupt ignoriert, jedoch nicht sicher zwangsweise beenden. Der feste Worker-Pool und die
 * begrenzte Queue verhindern in diesem Fall zumindest ein unbeschränktes Anwachsen weiterlaufender
 * Parser. Für eine harte Abbruchgarantie ist weiterhin ein separater Sandbox-Prozess erforderlich.
 */
final class ParserExecution {
    private static final int MAX_CONCURRENT_PARSERS = 4;
    private static final int MAX_QUEUED_PARSERS = 8;
    private static final Bulkhead SHARED = new Bulkhead(
            MAX_CONCURRENT_PARSERS, MAX_QUEUED_PARSERS, "evidence-parser-");

    private ParserExecution() {
    }

    static <T> T within(Duration timeout, CheckedSupplier<T> parser) {
        return SHARED.within(timeout, parser);
    }

    /** Eigenständige Instanzen erlauben deterministische Sättigungs- und Timeouttests. */
    static final class Bulkhead implements AutoCloseable {
        private final ThreadPoolExecutor executor;

        Bulkhead(int parallelism, int queueCapacity) {
            this(parallelism, queueCapacity, "evidence-parser-test-");
        }

        private Bulkhead(int parallelism, int queueCapacity, String threadNamePrefix) {
            if (parallelism < 1) throw new IllegalArgumentException("parallelism muss positiv sein");
            if (queueCapacity < 1) throw new IllegalArgumentException("queueCapacity muss positiv sein");
            executor = new ThreadPoolExecutor(
                    parallelism,
                    parallelism,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(queueCapacity),
                    Thread.ofPlatform().daemon(true).name(threadNamePrefix, 0).factory(),
                    new ThreadPoolExecutor.AbortPolicy());
        }

        <T> T within(Duration timeout, CheckedSupplier<T> parser) {
            Objects.requireNonNull(timeout, "timeout");
            Objects.requireNonNull(parser, "parser");
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout muss positiv sein");
            }

            FutureTask<T> task = new FutureTask<>(parser::get);
            try {
                executor.execute(task);
            } catch (RejectedExecutionException exception) {
                throw new InvalidUploadException(
                        "Die Dokumentverarbeitung ist vorübergehend ausgelastet.");
            }

            try {
                return task.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException exception) {
                cancelAndRemove(task);
                throw new InvalidUploadException(
                        "Das Dokument überschreitet das Verarbeitungszeitlimit.");
            } catch (InterruptedException exception) {
                cancelAndRemove(task);
                Thread.currentThread().interrupt();
                throw new InvalidUploadException("Die Dokumentverarbeitung wurde unterbrochen.");
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof InvalidUploadException invalidUploadException) {
                    throw invalidUploadException;
                }
                throw new InvalidUploadException(
                        "Das Dokument konnte nicht sicher verarbeitet werden.");
            }
        }

        int queuedTasks() {
            return executor.getQueue().size();
        }

        int largestPoolSize() {
            return executor.getLargestPoolSize();
        }

        private void cancelAndRemove(FutureTask<?> task) {
            task.cancel(true);
            executor.remove(task);
            executor.purge();
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
