package com.certaaccess.evidencerfp.evidence;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserExecutionTest {
    @Test
    void cancelsWorkAfterConfiguredTimeout() {
        assertThatThrownBy(() -> ParserExecution.within(Duration.ofMillis(10), () -> {
            Thread.sleep(Duration.ofSeconds(2));
            return "zu spät";
        })).isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Verarbeitungszeitlimit");
    }

    @Test
    void rejectsAdditionalWorkWhenNonCooperativeParserAndQueueFillBulkhead() throws Exception {
        CountDownLatch parserStarted = new CountDownLatch(1);
        CountDownLatch releaseParser = new CountDownLatch(1);
        AtomicReference<Throwable> queuedFailure = new AtomicReference<>();

        try (ParserExecution.Bulkhead bulkhead = new ParserExecution.Bulkhead(1, 1)) {
            assertThatThrownBy(() -> bulkhead.within(Duration.ofMillis(250), () -> {
                parserStarted.countDown();
                awaitIgnoringInterrupts(releaseParser);
                return "fertig";
            })).isInstanceOf(InvalidUploadException.class)
                    .hasMessageContaining("Verarbeitungszeitlimit");
            assertThat(parserStarted.await(1, TimeUnit.SECONDS)).isTrue();

            Thread queuedCaller = Thread.ofVirtual().start(() -> {
                try {
                    bulkhead.within(Duration.ofSeconds(2), () -> "wartend");
                } catch (Throwable throwable) {
                    queuedFailure.set(throwable);
                }
            });
            awaitQueueSize(bulkhead, 1);

            assertThatThrownBy(() -> bulkhead.within(Duration.ofSeconds(1), () -> "zu viel"))
                    .isInstanceOf(InvalidUploadException.class)
                    .hasMessageContaining("vorübergehend ausgelastet");
            assertThat(bulkhead.largestPoolSize()).isEqualTo(1);

            releaseParser.countDown();
            queuedCaller.join(Duration.ofSeconds(1));
            assertThat(queuedCaller.isAlive()).isFalse();
            assertThat(queuedFailure.get()).isNull();
        } finally {
            releaseParser.countDown();
        }
    }

    @Test
    void removesTimedOutQueuedParserBeforeItCanRun() throws Exception {
        CountDownLatch parserStarted = new CountDownLatch(1);
        CountDownLatch releaseParser = new CountDownLatch(1);
        AtomicBoolean queuedParserRan = new AtomicBoolean(false);

        try (ParserExecution.Bulkhead bulkhead = new ParserExecution.Bulkhead(1, 1)) {
            assertThatThrownBy(() -> bulkhead.within(Duration.ofMillis(250), () -> {
                parserStarted.countDown();
                awaitIgnoringInterrupts(releaseParser);
                return "fertig";
            })).isInstanceOf(InvalidUploadException.class);
            assertThat(parserStarted.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> bulkhead.within(Duration.ofMillis(25), () -> {
                queuedParserRan.set(true);
                return "zu spät";
            })).isInstanceOf(InvalidUploadException.class)
                    .hasMessageContaining("Verarbeitungszeitlimit");
            assertThat(bulkhead.queuedTasks()).isZero();

            releaseParser.countDown();
            Thread.sleep(50);
            assertThat(queuedParserRan).isFalse();
        } finally {
            releaseParser.countDown();
        }
    }

    private static void awaitIgnoringInterrupts(CountDownLatch release) {
        boolean interrupted = false;
        while (release.getCount() > 0) {
            try {
                release.await();
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static void awaitQueueSize(ParserExecution.Bulkhead bulkhead, int expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (bulkhead.queuedTasks() != expected && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertThat(bulkhead.queuedTasks()).isEqualTo(expected);
    }
}
