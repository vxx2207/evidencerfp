package com.certaaccess.evidencerfp.evidence;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Fuehrt einen Parser in einem begrenzten virtuellen Thread aus. */
final class ParserExecution {
    private ParserExecution() {
    }

    static <T> T within(Duration timeout, CheckedSupplier<T> parser) {
        FutureTask<T> task = new FutureTask<>(parser::get);
        Thread worker = Thread.ofVirtual().name("evidence-parser-").start(task);
        try {
            return task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            throw new InvalidUploadException("Das Dokument überschreitet das Verarbeitungszeitlimit.");
        } catch (InterruptedException exception) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new InvalidUploadException("Die Dokumentverarbeitung wurde unterbrochen.");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof InvalidUploadException invalidUploadException) {
                throw invalidUploadException;
            }
            throw new InvalidUploadException("Das Dokument konnte nicht sicher verarbeitet werden.");
        } finally {
            if (task.isCancelled()) {
                worker.interrupt();
            }
        }
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
