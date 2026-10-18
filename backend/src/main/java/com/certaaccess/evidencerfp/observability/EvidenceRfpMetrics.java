package com.certaaccess.evidencerfp.observability;

import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class EvidenceRfpMetrics {
    private final MeterRegistry registry;
    private final Map<String, Timer> stageTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();

    public EvidenceRfpMetrics(MeterRegistry registry,
                              @Qualifier("analysisTaskExecutor") ThreadPoolTaskExecutor executor) {
        this.registry = registry;
        Gauge.builder("evidencerfp.worker.active", executor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Aktiv verarbeitete Analysejobs").register(registry);
        Gauge.builder("evidencerfp.worker.queue.size", executor,
                        value -> value.getThreadPoolExecutor().getQueue().size())
                .description("Im lokalen Executor wartende Analysejobs").register(registry);
    }

    public <T> T timed(String stage, Supplier<T> action) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return action.get();
        } finally {
            sample.stop(stageTimers.computeIfAbsent(stage, value -> Timer.builder("evidencerfp.pipeline.duration")
                    .description("Dauer eines Verarbeitungsschritts")
                    .tag("stage", value).publishPercentileHistogram().register(registry)));
        }
    }

    public void answer(AnswerStatus status) {
        Counter.builder("evidencerfp.answers")
                .description("Erzeugte Antworten nach Evidenzstatus")
                .tag("status", status.name()).register(registry).increment();
    }

    public void rejectedCitation(String check) {
        rejectedCitation(check, 1);
    }

    public void rejectedCitation(String check, int amount) {
        if (amount <= 0) return;
        Counter.builder("evidencerfp.citations.rejected")
                .description("Fail-closed verworfene Zitations- oder Claimprüfungen")
                .tag("check", check).register(registry).increment(amount);
    }

    public void parserRejected(String parser) {
        Counter.builder("evidencerfp.parser.rejections")
                .description("Kontrolliert abgelehnte Dokumente")
                .tag("parser", parser).register(registry).increment();
    }

    public void error(String code) {
        String safeCode = code == null || !code.matches("[A-Z0-9_]{2,64}") ? "UNKNOWN" : code;
        errorCounters.computeIfAbsent(safeCode, value -> Counter.builder("evidencerfp.errors")
                .description("Sichere Fehlercodes der API und Jobverarbeitung")
                .tag("code", value).register(registry)).increment();
    }
}
