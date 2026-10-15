package com.certaaccess.evidencerfp.analysis.domain;

import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisStateMachineTest {

    @Test
    void resetsApprovalWhenApprovedAnswerTextChanges() {
        Analysis analysis = new DemoAnalysisFactory().create();
        QuestionResult approved = analysis.updateQuestion("RFP-001", null, ReviewStatus.APPROVED);
        assertThat(approved.reviewStatus()).isEqualTo(ReviewStatus.APPROVED);

        QuestionResult changed = analysis.updateQuestion(
                "RFP-001", approved.answer() + " Redaktionell geändert.", ReviewStatus.APPROVED);

        assertThat(changed.reviewStatus()).isEqualTo(ReviewStatus.DRAFT);
        assertThat(changed.answer()).endsWith("Redaktionell geändert.");
    }
}
