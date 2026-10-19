package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJob;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisPhase;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisUpload;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisRepository {
    Analysis save(Analysis analysis);

    AnalysisSubmission enqueue(Analysis analysis, AnalysisJob job, List<AnalysisUpload> uploads,
                               String idempotencyKey, String requestHash);

    Optional<Analysis> findById(UUID id);

    Optional<AnalysisJob> findJobByAnalysisId(UUID id);

    Optional<AnalysisStatusSnapshot> findStatusSnapshotById(UUID id);

    List<AnalysisUpload> findUploads(UUID analysisId);

    List<UUID> findRunnableJobIds(Instant now, int maxResults);

    List<UUID> failExhaustedJobs(Instant now);

    Optional<AnalysisJob> claimJob(UUID analysisId, String leaseOwner, Instant now, Instant leaseUntil);

    boolean renewLease(UUID analysisId, String leaseOwner, Instant heartbeatAt, Instant leaseUntil);

    boolean advanceJob(UUID analysisId, String leaseOwner, AnalysisPhase phase, int progressPercent,
                       Instant now, Instant leaseUntil);

    boolean completeJob(Analysis analysis, String leaseOwner, Instant now);

    boolean failJob(UUID analysisId, String leaseOwner, String failureCode, Instant now);

    QuestionResult updateQuestion(UUID analysisId, String questionId, long expectedRevision,
                                  String answer, ReviewStatus reviewStatus, Instant occurredAt);

    List<UUID> claimCleanupTasks(String leaseOwner, Instant now, Instant leaseUntil, int maxResults);

    boolean completeCleanup(UUID analysisId, String leaseOwner, Instant now);

    boolean retryCleanup(UUID analysisId, String leaseOwner, Instant now, Instant nextAttemptAt,
                         String failureCode);

    boolean isUploadStorageTracked(UUID analysisId);

    boolean deleteById(UUID id, Instant now);

    default boolean deleteById(UUID id) {
        return deleteById(id, Instant.now());
    }
}
