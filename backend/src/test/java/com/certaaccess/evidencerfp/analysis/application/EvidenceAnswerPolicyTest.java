package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.Citation;
import com.certaaccess.evidencerfp.analysis.domain.FindingType;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import com.certaaccess.evidencerfp.evidence.EvidenceChunk;
import com.certaaccess.evidencerfp.evidence.RfpQuestion;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceAnswerPolicyTest {
    private static final RfpQuestion QUESTION = new RfpQuestion(
            "RFP-016", "Wie lange werden Audit-Ereignisse aufbewahrt?", "Audit");
    private final EvidenceAnswerPolicy policy = new EvidenceAnswerPolicy(
            Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC), 365);

    @Test
    void forcesPartialAndBlockingFindingForConflictingCitedChunks() {
        EvidenceChunk oldPolicy = chunk("policy.pdf", "Audit-Ereignisse werden 365 Tage aufbewahrt.", null);
        EvidenceChunk newPolicy = chunk("operations.pdf", "Audit-Ereignisse werden 400 Tage aufbewahrt.", null);

        QuestionResult result = policy.enforce(QUESTION,
                supported(oldPolicy, newPolicy), List.of(oldPolicy, newPolicy));

        assertThat(result.answerStatus()).isEqualTo(AnswerStatus.PARTIAL);
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.type()).isEqualTo(FindingType.CONFLICTING_EVIDENCE);
            assertThat(finding.ruleId()).isEqualTo("EVIDENCE-002");
            assertThat(finding.blocksApproval()).isTrue();
        });
    }

    @Test
    void forcesPartialAndBlockingFindingForStaleCitedChunk() {
        EvidenceChunk stale = chunk("policy.pdf", "Audit-Ereignisse werden 365 Tage aufbewahrt.",
                LocalDate.parse("2024-01-01"));

        QuestionResult result = policy.enforce(QUESTION, supported(stale), List.of(stale));

        assertThat(result.answerStatus()).isEqualTo(AnswerStatus.PARTIAL);
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.type()).isEqualTo(FindingType.STALE_DOCUMENT);
            assertThat(finding.ruleId()).isEqualTo("EVIDENCE-003");
            assertThat(finding.blocksApproval()).isTrue();
        });
    }

    @Test
    void keepsSupportedStatusForCurrentConsistentEvidence() {
        EvidenceChunk first = chunk("policy.pdf", "Audit-Ereignisse werden 365 Tage aufbewahrt.",
                LocalDate.parse("2026-09-01"));
        EvidenceChunk second = chunk("operations.pdf", "Die Aufbewahrung beträgt ebenfalls 365 Tage.",
                LocalDate.parse("2026-09-10"));

        QuestionResult result = policy.enforce(QUESTION, supported(first, second), List.of(first, second));

        assertThat(result.answerStatus()).isEqualTo(AnswerStatus.SUPPORTED);
        assertThat(result.findings()).isEmpty();
    }

    private QuestionResult supported(EvidenceChunk... chunks) {
        List<Citation> citations = java.util.Arrays.stream(chunks)
                .map(chunk -> new Citation(chunk.id(), chunk.documentName(), chunk.locator(), chunk.content()))
                .toList();
        return new QuestionResult(QUESTION.id(), QUESTION.question(), QUESTION.category(), "Belegte Antwort",
                AnswerStatus.SUPPORTED, ReviewStatus.DRAFT, citations, null, List.of());
    }

    private EvidenceChunk chunk(String document, String content, LocalDate date) {
        return new EvidenceChunk(UUID.randomUUID(), document, "application/pdf", "Seite 1", content, date);
    }
}
