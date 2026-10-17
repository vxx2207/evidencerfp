package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.EvidenceDocument;
import com.certaaccess.evidencerfp.analysis.domain.Finding;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewEvent;

import java.util.List;
import java.util.UUID;

public record AnalysisResultResponse(
        UUID id,
        String customerName,
        boolean synthetic,
        AnalysisStatus status,
        List<QuestionResult> questions,
        List<Finding> findings,
        List<EvidenceDocument> evidenceDocuments,
        List<ReviewEvent> reviewEvents
) {
    public static AnalysisResultResponse from(Analysis analysis) {
        return new AnalysisResultResponse(analysis.id(), analysis.customerName(), analysis.synthetic(),
                analysis.status(), analysis.questions(), analysis.findings(), analysis.evidenceDocuments(),
                analysis.reviewEvents());
    }
}
