package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnswerClaim;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.Citation;
import com.certaaccess.evidencerfp.analysis.domain.EvidenceDocument;
import com.certaaccess.evidencerfp.analysis.domain.EvidencePassage;
import com.certaaccess.evidencerfp.analysis.domain.Finding;
import com.certaaccess.evidencerfp.analysis.domain.FindingSeverity;
import com.certaaccess.evidencerfp.analysis.domain.FindingType;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewAction;
import com.certaaccess.evidencerfp.analysis.domain.ReviewActor;
import com.certaaccess.evidencerfp.analysis.domain.ReviewEvent;
import com.certaaccess.evidencerfp.analysis.domain.ReviewProvenance;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import com.certaaccess.evidencerfp.evidence.RetrievalChannel;
import com.certaaccess.evidencerfp.evidence.RetrievalProvenance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Stabiler API-Vertrag; keine veränderlichen Domainobjekte verlassen den Controller. */
public record AnalysisResultResponse(
        UUID id,
        String customerName,
        boolean synthetic,
        AnalysisStatus status,
        long reviewRevision,
        List<QuestionResponse> questions,
        List<FindingResponse> findings,
        List<EvidenceDocumentResponse> evidenceDocuments,
        List<ReviewEventResponse> reviewEvents,
        RunMetadataResponse runMetadata
) {
    public static AnalysisResultResponse from(Analysis analysis, RunMetadataResponse runMetadata) {
        long reviewRevision = analysis.questions().stream().mapToLong(QuestionResult::revision).sum();
        return new AnalysisResultResponse(analysis.id(), analysis.customerName(), analysis.synthetic(),
                analysis.status(), reviewRevision,
                analysis.questions().stream().map(QuestionResponse::from).toList(),
                analysis.findings().stream().map(FindingResponse::from).toList(),
                analysis.evidenceDocuments().stream().map(EvidenceDocumentResponse::from).toList(),
                analysis.reviewEvents().stream().map(ReviewEventResponse::from).toList(), runMetadata);
    }

    public record QuestionResponse(
            String id,
            String question,
            String category,
            String answer,
            AnswerStatus answerStatus,
            ReviewStatus reviewStatus,
            List<CitationResponse> citations,
            String internalClarification,
            List<FindingResponse> findings,
            List<ClaimResponse> claims,
            long revision
    ) {
        static QuestionResponse from(QuestionResult question) {
            return new QuestionResponse(question.id(), question.question(), question.category(),
                    question.answer(), question.answerStatus(), question.reviewStatus(),
                    question.citations().stream().map(CitationResponse::from).toList(),
                    question.internalClarification(),
                    question.findings().stream().map(FindingResponse::from).toList(),
                    question.claims().stream().map(ClaimResponse::from).toList(), question.revision());
        }
    }

    public record CitationResponse(
            UUID id,
            UUID chunkId,
            String documentId,
            String documentName,
            String locator,
            String quote,
            String relevance,
            RetrievalResponse retrieval
    ) {
        static CitationResponse from(Citation citation) {
            return new CitationResponse(citation.chunkId(), citation.chunkId(), citation.documentName(),
                    citation.documentName(), citation.locator(), citation.quote(),
                    "Serverseitig validierte Evidenz",
                    RetrievalResponse.from(citation.retrievalProvenance()));
        }
    }

    public record RetrievalResponse(
            Set<RetrievalChannel> methods,
            Integer denseRank,
            Integer fullTextRank,
            double fusedScore,
            String chunkHash,
            String retrievalVersion,
            String embeddingModel
    ) {
        static RetrievalResponse from(RetrievalProvenance provenance) {
            if (provenance == null) return null;
            return new RetrievalResponse(provenance.channels(), provenance.denseRank(),
                    provenance.fullTextRank(), provenance.fusedScore(), provenance.chunkSha256(),
                    provenance.retrievalVersion(), provenance.embeddingModel());
        }
    }

    public record ClaimResponse(String text, List<UUID> citationIds) {
        static ClaimResponse from(AnswerClaim claim) {
            return new ClaimResponse(claim.text(), claim.citationChunkIds());
        }
    }

    public record FindingResponse(
            UUID id,
            FindingType type,
            FindingSeverity severity,
            String ruleId,
            String title,
            String description,
            String affectedObject,
            String recommendation,
            boolean blocksApproval
    ) {
        static FindingResponse from(Finding finding) {
            return new FindingResponse(finding.id(), finding.type(), finding.severity(), finding.ruleId(),
                    finding.title(), finding.description(), finding.affectedObject(), finding.recommendation(),
                    finding.blocksApproval());
        }
    }

    public record EvidenceDocumentResponse(
            String name,
            String mediaType,
            int chunkCount,
            LocalDate documentDate,
            boolean stale,
            List<EvidencePassageResponse> passages
    ) {
        static EvidenceDocumentResponse from(EvidenceDocument document) {
            return new EvidenceDocumentResponse(document.name(), document.mediaType(), document.chunkCount(),
                    document.documentDate(), document.stale(),
                    document.passages().stream().map(EvidencePassageResponse::from).toList());
        }
    }

    public record EvidencePassageResponse(UUID chunkId, String locator, String content) {
        static EvidencePassageResponse from(EvidencePassage passage) {
            return new EvidencePassageResponse(passage.chunkId(), passage.locator(), passage.content());
        }
    }

    public record ReviewEventResponse(
            UUID id,
            String questionId,
            ReviewActor actor,
            ReviewAction action,
            ReviewProvenance provenance,
            Instant occurredAt,
            String beforeAnswer,
            String afterAnswer,
            ReviewStatus beforeReviewStatus,
            ReviewStatus afterReviewStatus
    ) {
        static ReviewEventResponse from(ReviewEvent event) {
            return new ReviewEventResponse(event.id(), event.questionId(), event.actor(), event.action(),
                    event.provenance(), event.occurredAt(), event.beforeAnswer(), event.afterAnswer(),
                    event.beforeReviewStatus(), event.afterReviewStatus());
        }
    }

    public record RunMetadataResponse(
            String retrievalVersion,
            String promptVersion,
            String embeddingModel,
            String chatModel,
            String citationValidation,
            String dataBoundary
    ) { }
}
