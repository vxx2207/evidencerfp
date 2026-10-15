package com.certaaccess.evidencerfp.analysis.domain;

import java.util.List;
import java.util.Objects;

public record QuestionResult(
        String id,
        String question,
        String category,
        String answer,
        AnswerStatus answerStatus,
        ReviewStatus reviewStatus,
        List<Citation> citations,
        String internalClarification,
        List<Finding> findings
) {
    public QuestionResult {
        citations = citations == null ? List.of() : List.copyOf(citations);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public QuestionResult update(String newAnswer, ReviewStatus newReviewStatus) {
        boolean approvedAnswerChanged = reviewStatus == ReviewStatus.APPROVED
                && newAnswer != null && !Objects.equals(answer, newAnswer);
        return new QuestionResult(id, question, category,
                newAnswer == null ? answer : newAnswer,
                answerStatus,
                approvedAnswerChanged ? ReviewStatus.DRAFT
                        : newReviewStatus == null ? reviewStatus : newReviewStatus,
                citations, internalClarification, findings);
    }
}
