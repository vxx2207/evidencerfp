package com.certaaccess.evidencerfp.analysis.infrastructure;

import com.certaaccess.evidencerfp.analysis.application.AnalysisRepository;
import com.certaaccess.evidencerfp.analysis.application.AnalysisNotReadyException;
import com.certaaccess.evidencerfp.analysis.application.AnalysisSubmission;
import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import com.certaaccess.evidencerfp.analysis.application.EditConflictException;
import com.certaaccess.evidencerfp.analysis.application.IdempotencyKeyReusedException;
import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJob;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJobStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisPhase;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisUpload;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@Profile("!production")
public class InMemoryAnalysisRepository implements AnalysisRepository {
    private final ConcurrentMap<UUID, Analysis> analyses = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, AnalysisJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, List<AnalysisUpload>> uploads = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, IdempotencyRecord> idempotency = new ConcurrentHashMap<>();
    private final DemoAnalysisFactory demoAnalysisFactory;

    public InMemoryAnalysisRepository(DemoAnalysisFactory demoAnalysisFactory) {
        this.demoAnalysisFactory = demoAnalysisFactory;
    }

    @PostConstruct
    void seedDemo() {
        Analysis demo = demoAnalysisFactory.create();
        analyses.put(demo.id(), demo);
    }

    @Override
    public Analysis save(Analysis analysis) {
        analyses.put(analysis.id(), analysis);
        return analysis;
    }

    @Override
    public synchronized AnalysisSubmission enqueue(Analysis analysis, AnalysisJob job,
                                                   List<AnalysisUpload> stagedUploads,
                                                   String idempotencyKey, String requestHash) {
        if (idempotencyKey != null) {
            IdempotencyRecord existing = idempotency.get(idempotencyKey);
            if (existing != null) {
                if (!existing.requestHash().equals(requestHash)) throw new IdempotencyKeyReusedException();
                Analysis existingAnalysis = analyses.get(existing.analysisId());
                if (existingAnalysis != null) return new AnalysisSubmission(existingAnalysis, false);
            }
        }
        analyses.put(analysis.id(), analysis);
        jobs.put(analysis.id(), job);
        uploads.put(analysis.id(), List.copyOf(stagedUploads));
        if (idempotencyKey != null) {
            idempotency.put(idempotencyKey, new IdempotencyRecord(requestHash, analysis.id()));
        }
        return new AnalysisSubmission(analysis, true);
    }

    @Override
    public Optional<Analysis> findById(UUID id) {
        return Optional.ofNullable(analyses.get(id));
    }

    @Override
    public Optional<AnalysisJob> findJobByAnalysisId(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public List<AnalysisUpload> findUploads(UUID analysisId) {
        return uploads.getOrDefault(analysisId, List.of());
    }

    @Override
    public List<UUID> findRunnableJobIds(Instant now, int maxResults) {
        return jobs.values().stream()
                .filter(job -> !job.cancelRequested() && job.attempt() < job.maxAttempts())
                .filter(job -> job.status() == AnalysisJobStatus.QUEUED
                        || job.status() == AnalysisJobStatus.PROCESSING
                        && job.leaseUntil() != null && !job.leaseUntil().isAfter(now))
                .sorted(Comparator.comparing(AnalysisJob::createdAt))
                .limit(maxResults)
                .map(AnalysisJob::analysisId)
                .toList();
    }

    @Override
    public synchronized List<UUID> failExhaustedJobs(Instant now) {
        List<UUID> exhausted = jobs.values().stream()
                .filter(job -> job.status() == AnalysisJobStatus.PROCESSING)
                .filter(job -> !job.cancelRequested() && job.attempt() >= job.maxAttempts())
                .filter(job -> job.leaseUntil() != null && !job.leaseUntil().isAfter(now))
                .map(AnalysisJob::analysisId).toList();
        for (UUID id : exhausted) {
            AnalysisJob job = jobs.get(id);
            Analysis analysis = analyses.get(id);
            jobs.put(id, copy(job, AnalysisPhase.FAILED, job.progressPercent(), now, null,
                    AnalysisJobStatus.FAILED, "JOB_RETRIES_EXHAUSTED", now));
            if (analysis != null) {
                analyses.put(id, new Analysis(analysis.id(), analysis.customerName(), analysis.synthetic(),
                        AnalysisStatus.FAILED, analysis.createdAt(), now, List.of(), List.of(), List.of()));
            }
        }
        return exhausted;
    }

    @Override
    public synchronized Optional<AnalysisJob> claimJob(UUID analysisId, String leaseOwner,
                                                       Instant now, Instant leaseUntil) {
        AnalysisJob current = jobs.get(analysisId);
        if (current == null || current.cancelRequested() || current.attempt() >= current.maxAttempts()) {
            return Optional.empty();
        }
        boolean runnable = current.status() == AnalysisJobStatus.QUEUED
                || current.status() == AnalysisJobStatus.PROCESSING
                && current.leaseUntil() != null && !current.leaseUntil().isAfter(now);
        if (!runnable) return Optional.empty();
        AnalysisJob claimed = new AnalysisJob(current.analysisId(), AnalysisJobStatus.PROCESSING,
                AnalysisPhase.PARSING, 5, current.attempt() + 1, current.maxAttempts(), leaseOwner,
                leaseUntil, now, null, false, current.createdAt(), now,
                current.startedAt() == null ? now : current.startedAt());
        jobs.put(analysisId, claimed);
        return Optional.of(claimed);
    }

    @Override
    public synchronized boolean renewLease(UUID analysisId, String leaseOwner, Instant heartbeatAt,
                                           Instant leaseUntil) {
        AnalysisJob current = jobs.get(analysisId);
        if (!owned(current, leaseOwner)) return false;
        jobs.put(analysisId, copy(current, current.phase(), current.progressPercent(), heartbeatAt,
                leaseUntil, current.status(), current.failureCode(), heartbeatAt));
        return true;
    }

    @Override
    public synchronized boolean advanceJob(UUID analysisId, String leaseOwner, AnalysisPhase phase,
                                           int progressPercent, Instant now, Instant leaseUntil) {
        AnalysisJob current = jobs.get(analysisId);
        if (!owned(current, leaseOwner)) return false;
        jobs.put(analysisId, copy(current, phase, progressPercent, now, leaseUntil,
                AnalysisJobStatus.PROCESSING, null, now));
        return true;
    }

    @Override
    public synchronized boolean completeJob(Analysis analysis, String leaseOwner, Instant now) {
        AnalysisJob current = jobs.get(analysis.id());
        if (!owned(current, leaseOwner) || !analyses.containsKey(analysis.id())) return false;
        analyses.put(analysis.id(), analysis);
        jobs.put(analysis.id(), copy(current, AnalysisPhase.COMPLETED, 100, now, null,
                AnalysisJobStatus.COMPLETED, null, now));
        return true;
    }

    @Override
    public synchronized boolean failJob(UUID analysisId, String leaseOwner, String failureCode, Instant now) {
        AnalysisJob current = jobs.get(analysisId);
        Analysis existing = analyses.get(analysisId);
        if (!owned(current, leaseOwner) || existing == null) return false;
        jobs.put(analysisId, copy(current, AnalysisPhase.FAILED, current.progressPercent(), now, null,
                AnalysisJobStatus.FAILED, failureCode, now));
        analyses.put(analysisId, new Analysis(existing.id(), existing.customerName(), existing.synthetic(),
                AnalysisStatus.FAILED, existing.createdAt(), now, List.of(), List.of(), List.of()));
        return true;
    }

    @Override
    public QuestionResult updateQuestion(UUID analysisId, String questionId, long expectedRevision,
                                         String answer, ReviewStatus reviewStatus, Instant occurredAt) {
        Analysis analysis = analyses.get(analysisId);
        if (analysis == null) throw new com.certaaccess.evidencerfp.analysis.application.AnalysisNotFoundException(analysisId);
        synchronized (analysis) {
            if (analysis.status() != AnalysisStatus.COMPLETED) {
                throw new AnalysisNotReadyException(analysis.status());
            }
            QuestionResult current = analysis.questions().stream()
                    .filter(question -> question.id().equals(questionId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Frage wurde nicht gefunden: " + questionId));
            if (current.revision() != expectedRevision) throw new EditConflictException();
            return analysis.updateQuestion(questionId, answer, reviewStatus, occurredAt);
        }
    }

    @Override
    public void deleteUploadMetadata(UUID analysisId) {
        uploads.remove(analysisId);
    }

    @Override
    public synchronized boolean deleteById(UUID id) {
        Analysis removed = analyses.remove(id);
        if (removed == null) return false;
        AnalysisJob current = jobs.get(id);
        if (current != null) {
            Instant now = Instant.now();
            jobs.put(id, copy(current, AnalysisPhase.CANCELLED, current.progressPercent(), now, null,
                    AnalysisJobStatus.CANCELLED, null, now));
        }
        jobs.remove(id);
        uploads.remove(id);
        idempotency.entrySet().removeIf(entry -> entry.getValue().analysisId().equals(id));
        return true;
    }

    private boolean owned(AnalysisJob job, String leaseOwner) {
        return job != null && job.status() == AnalysisJobStatus.PROCESSING && !job.cancelRequested()
                && leaseOwner.equals(job.leaseOwner());
    }

    private AnalysisJob copy(AnalysisJob current, AnalysisPhase phase, int progress, Instant heartbeat,
                             Instant leaseUntil, AnalysisJobStatus status, String failureCode, Instant updatedAt) {
        return new AnalysisJob(current.analysisId(), status, phase, progress, current.attempt(),
                current.maxAttempts(), leaseUntil == null ? null : current.leaseOwner(), leaseUntil,
                heartbeat, failureCode, current.cancelRequested(), current.createdAt(), updatedAt,
                current.startedAt());
    }

    private record IdempotencyRecord(String requestHash, UUID analysisId) { }
}
