package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisRunMetadata;
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
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Stabiler API-Vertrag; keine veränderlichen Domainobjekte verlassen den Controller. */
public record AnalysisResultResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String customerName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean synthetic,
        @Schema(allowableValues = "COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
        AnalysisStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long reviewRevision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<QuestionResponse> questions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<FindingResponse> findings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<EvidenceDocumentResponse> evidenceDocuments,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ReviewEventResponse> reviewEvents,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        RunMetadataResponse runMetadata
) {
    public static AnalysisResultResponse from(Analysis analysis, String dataBoundary) {
        long reviewRevision = analysis.questions().stream().mapToLong(QuestionResult::revision).sum();
        return new AnalysisResultResponse(analysis.id(), analysis.customerName(), analysis.synthetic(),
                analysis.status(), reviewRevision,
                analysis.questions().stream().map(QuestionResponse::from).toList(),
                analysis.findings().stream().map(FindingResponse::from).toList(),
                analysis.evidenceDocuments().stream().map(EvidenceDocumentResponse::from).toList(),
                analysis.reviewEvents().stream().map(ReviewEventResponse::from).toList(),
                RunMetadataResponse.from(analysis.runMetadata(), dataBoundary));
    }

    public record QuestionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String question,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String category,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String answer,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            AnswerStatus answerStatus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ReviewStatus reviewStatus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<CitationResponse> citations,
            String internalClarification,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<FindingResponse> findings,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<ClaimResponse> claims,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @Min(0)
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
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID chunkId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String documentId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String documentName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String locator,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String quote,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
            @Min(1)
            Integer denseRank,
            @Min(1)
            Integer fullTextRank,
            @DecimalMin("0")
            double fusedScore,
            @Pattern(regexp = "^[a-f0-9]{64}$")
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

    public record ClaimResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String text,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Size(min = 1) List<UUID> citationIds
    ) {
        static ClaimResponse from(AnswerClaim claim) {
            return new ClaimResponse(claim.text(), claim.citationChunkIds());
        }
    }

    public record FindingResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            FindingType type,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            FindingSeverity severity,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String ruleId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String description,
            String affectedObject,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String recommendation,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String questionId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ReviewActor actor,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ReviewAction action,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ReviewProvenance provenance,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant occurredAt,
            String beforeAnswer,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String afterAnswer,
            ReviewStatus beforeReviewStatus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ReviewStatus afterReviewStatus
    ) {
        static ReviewEventResponse from(ReviewEvent event) {
            return new ReviewEventResponse(event.id(), event.questionId(), event.actor(), event.action(),
                    event.provenance(), event.occurredAt(), event.beforeAnswer(), event.afterAnswer(),
                    event.beforeReviewStatus(), event.afterReviewStatus());
        }
    }

    public record RunMetadataResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String retrievalVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String promptVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String embeddingModel,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String chatModel,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String citationValidation,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String dataBoundary
    ) {
        private static RunMetadataResponse from(AnalysisRunMetadata metadata, String dataBoundary) {
            return new RunMetadataResponse(metadata.retrievalVersion(), metadata.promptVersion(),
                    metadata.embeddingModel(), metadata.chatModel(),
                    "Chunk-Zulässigkeit, exaktes Zitat, Zahlen, Entitäten und Negationen geprüft",
                    dataBoundary);
        }
    }
}
