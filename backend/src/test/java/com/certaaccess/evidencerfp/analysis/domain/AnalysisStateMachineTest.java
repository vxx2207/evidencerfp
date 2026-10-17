package com.certaaccess.evidencerfp.analysis.domain;

import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisStateMachineTest {

    @Test
    void resetsApprovalWhenApprovedAnswerTextChanges() {
        Analysis analysis = new DemoAnalysisFactory().create();
        Instant approvedAt = Instant.parse("2026-10-15T09:00:00Z");
        Instant editedAt = Instant.parse("2026-10-15T09:01:00Z");
        QuestionResult approved = analysis.updateQuestion(
                "RFP-001", null, ReviewStatus.APPROVED, approvedAt);
        assertThat(approved.reviewStatus()).isEqualTo(ReviewStatus.APPROVED);

        QuestionResult changed = analysis.updateQuestion(
                "RFP-001", approved.answer() + " Redaktionell geändert.", ReviewStatus.APPROVED, editedAt);

        assertThat(changed.reviewStatus()).isEqualTo(ReviewStatus.DRAFT);
        assertThat(changed.answer()).endsWith("Redaktionell geändert.");
        assertThat(analysis.reviewEvents()).hasSize(26);
        assertThat(analysis.reviewEvents().getLast()).satisfies(event -> {
            assertThat(event.actor()).isEqualTo(ReviewActor.LOCAL_REVIEWER);
            assertThat(event.action()).isEqualTo(ReviewAction.ANSWER_AND_REVIEW_STATUS_CHANGED);
            assertThat(event.provenance()).isEqualTo(ReviewProvenance.HUMAN_EDITED);
            assertThat(event.occurredAt()).isEqualTo(editedAt);
            assertThat(event.beforeAnswer()).isEqualTo(approved.answer());
            assertThat(event.afterAnswer()).isEqualTo(changed.answer());
            assertThat(event.beforeReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
            assertThat(event.afterReviewStatus()).isEqualTo(ReviewStatus.DRAFT);
        });
    }

    @Test
    void doesNotAppendAnAuditEventForANoOpPatch() {
        Analysis analysis = new DemoAnalysisFactory().create();
        int originalEventCount = analysis.reviewEvents().size();

        analysis.updateQuestion("RFP-001", null, null, Instant.parse("2026-10-15T09:00:00Z"));

        assertThat(analysis.reviewEvents()).hasSize(originalEventCount);
    }

    @Test
    void treatsApprovedAndChangesRequiredAsDecisionsButDraftAsOpen() {
        Analysis analysis = new DemoAnalysisFactory().create();

        assertThat(analysis.reviewComplete()).isFalse();
        assertThatThrownBy(analysis::requireCompleteReview)
                .isInstanceOf(ReviewIncompleteException.class)
                .hasMessageContaining("Noch offene Fragen");

        for (QuestionResult question : analysis.questions()) {
            ReviewStatus decision = question.answerStatus() == AnswerStatus.NOT_FOUND
                    || question.findings().stream().anyMatch(Finding::blocksApproval)
                    ? ReviewStatus.CHANGES_REQUIRED
                    : ReviewStatus.APPROVED;
            analysis.updateQuestion(question.id(), null, decision);
        }

        assertThat(analysis.reviewComplete()).isTrue();
        assertThatCode(analysis::requireCompleteReview).doesNotThrowAnyException();
        assertThat(analysis.questions()).allMatch(question -> question.reviewStatus() != ReviewStatus.DRAFT);
    }

    @Test
    void allowsChangesRequiredForNotFoundAndBlockingFindings() {
        Analysis analysis = new DemoAnalysisFactory().create();
        QuestionResult notFound = analysis.questions().stream()
                .filter(question -> question.answerStatus() == AnswerStatus.NOT_FOUND)
                .findFirst().orElseThrow();

        QuestionResult decided = analysis.updateQuestion(
                notFound.id(), null, ReviewStatus.CHANGES_REQUIRED);

        assertThat(decided.reviewStatus()).isEqualTo(ReviewStatus.CHANGES_REQUIRED);
        assertThatThrownBy(() -> analysis.updateQuestion(notFound.id(), null, ReviewStatus.APPROVED))
                .isInstanceOf(ApprovalBlockedException.class);
    }
}
