package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

public record AnalysisStatusResponse(
        UUID id,
        AnalysisStatus status,
        int progressPercent,
        String phase,
        Instant createdAt,
        Instant completedAt,
        int totalQuestions,
        int reviewedQuestions
) {
    public static AnalysisStatusResponse from(Analysis analysis) {
        int reviewed = (int) analysis.questions().stream()
                .filter(question -> question.reviewStatus() != ReviewStatus.DRAFT).count();
        int progress = analysis.status() == AnalysisStatus.COMPLETED ? 100
                : analysis.status() == AnalysisStatus.FAILED ? 0 : 50;
        String phase = analysis.status() == AnalysisStatus.COMPLETED ? "Analyse abgeschlossen"
                : analysis.status() == AnalysisStatus.FAILED ? "Analyse fehlgeschlagen" : "Evidenz wird verarbeitet";
        return new AnalysisStatusResponse(analysis.id(), analysis.status(), progress, phase,
                analysis.createdAt(), analysis.completedAt(), analysis.questions().size(), reviewed);
    }
}
