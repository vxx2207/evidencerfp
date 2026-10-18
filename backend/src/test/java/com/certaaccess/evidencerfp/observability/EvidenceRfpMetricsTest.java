package com.certaaccess.evidencerfp.observability;

import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceRfpMetricsTest {
    @Test
    void recordsPipelineAnswersRejectionsErrorsAndWorkerGaugesWithoutContentLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.initialize();
        try {
            EvidenceRfpMetrics metrics = new EvidenceRfpMetrics(registry, executor);
            assertThat(metrics.timed("retrieval", () -> "ok")).isEqualTo("ok");
            metrics.answer(AnswerStatus.SUPPORTED);
            metrics.rejectedCitation("claim", 2);
            metrics.parserRejected("OoxmlPackageValidator");
            metrics.error("INVALID_MODEL_OUTPUT");

            assertThat(registry.get("evidencerfp.pipeline.duration").tag("stage", "retrieval")
                    .timer().count()).isEqualTo(1);
            assertThat(registry.get("evidencerfp.answers").tag("status", "SUPPORTED")
                    .counter().count()).isEqualTo(1);
            assertThat(registry.get("evidencerfp.citations.rejected").tag("check", "claim")
                    .counter().count()).isEqualTo(2);
            assertThat(registry.get("evidencerfp.parser.rejections")
                    .tag("parser", "OoxmlPackageValidator").counter().count()).isEqualTo(1);
            assertThat(registry.get("evidencerfp.errors").tag("code", "INVALID_MODEL_OUTPUT")
                    .counter().count()).isEqualTo(1);
            assertThat(registry.get("evidencerfp.worker.active").gauge().value()).isZero();
            assertThat(registry.get("evidencerfp.worker.queue.size").gauge().value()).isZero();
        } finally {
            executor.shutdown();
            registry.close();
        }
    }
}
