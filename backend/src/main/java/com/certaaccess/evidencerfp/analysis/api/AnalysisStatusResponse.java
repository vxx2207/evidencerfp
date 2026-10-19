package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJob;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJobStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisPhase;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import com.certaaccess.evidencerfp.analysis.application.AnalysisStatusSnapshot;

import java.time.Instant;
import java.util.UUID;

public record AnalysisStatusResponse(
        UUID id,
        AnalysisStatus status,
        int progressPercent,
        String phase,
        int attempt,
        Instant updatedAt,
        Instant heartbeatAt,
        String failureCode,
        Instant createdAt,
        Instant completedAt,
        int totalQuestions,
        int reviewedQuestions
) {
    public static AnalysisStatusResponse from(Analysis analysis) {
        AnalysisJobStatus jobStatus = switch (analysis.status()) {
            case PROCESSING -> AnalysisJobStatus.QUEUED;
            case COMPLETED -> AnalysisJobStatus.COMPLETED;
            case FAILED -> AnalysisJobStatus.FAILED;
        };
        AnalysisPhase phase = switch (analysis.status()) {
            case PROCESSING -> AnalysisPhase.QUEUED;
            case COMPLETED -> AnalysisPhase.COMPLETED;
            case FAILED -> AnalysisPhase.FAILED;
        };
        Instant updatedAt = analysis.completedAt() == null ? analysis.createdAt() : analysis.completedAt();
        return from(analysis, new AnalysisJob(analysis.id(), jobStatus, phase,
                analysis.status() == AnalysisStatus.COMPLETED ? 100 : 0, 0, 2,
                null, null, null, null, false, analysis.createdAt(), updatedAt, null));
    }

    public static AnalysisStatusResponse from(Analysis analysis, AnalysisJob job) {
        int reviewed = (int) analysis.questions().stream()
                .filter(question -> question.reviewStatus() != ReviewStatus.DRAFT).count();
        return new AnalysisStatusResponse(analysis.id(), analysis.status(), job.progressPercent(),
                job.phase().name(), job.attempt(), job.updatedAt(), job.heartbeatAt(), job.failureCode(),
                analysis.createdAt(), analysis.completedAt(), analysis.questions().size(), reviewed);
    }

    public static AnalysisStatusResponse from(AnalysisStatusSnapshot snapshot) {
        return snapshot.job().map(job -> from(snapshot.analysis(), job))
                .orElseGet(() -> from(snapshot.analysis()));
    }
}
