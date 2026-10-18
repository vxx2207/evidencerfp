package com.certaaccess.evidencerfp.analysis.domain;

import java.time.Instant;
import java.util.UUID;

public record AnalysisJob(
        UUID analysisId,
        AnalysisJobStatus status,
        AnalysisPhase phase,
        int progressPercent,
        int attempt,
        int maxAttempts,
        String leaseOwner,
        Instant leaseUntil,
        Instant heartbeatAt,
        String failureCode,
        boolean cancelRequested,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt
) {
    public static AnalysisJob queued(UUID analysisId, Instant now, int maxAttempts) {
        return new AnalysisJob(analysisId, AnalysisJobStatus.QUEUED, AnalysisPhase.QUEUED,
                0, 0, maxAttempts, null, null, null, null, false, now, now, null);
    }

    public boolean terminal() {
        return status == AnalysisJobStatus.COMPLETED || status == AnalysisJobStatus.FAILED
                || status == AnalysisJobStatus.CANCELLED;
    }
}
