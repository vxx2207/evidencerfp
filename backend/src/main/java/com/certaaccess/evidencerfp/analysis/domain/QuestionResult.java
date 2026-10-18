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
        List<Finding> findings,
        List<AnswerClaim> claims,
        long revision
) {
    public QuestionResult(String id, String question, String category, String answer,
                          AnswerStatus answerStatus, ReviewStatus reviewStatus,
                          List<Citation> citations, String internalClarification,
                          List<Finding> findings) {
        this(id, question, category, answer, answerStatus, reviewStatus, citations,
                internalClarification, findings, derivedClaims(answer, citations), 0);
    }

    public QuestionResult(String id, String question, String category, String answer,
                          AnswerStatus answerStatus, ReviewStatus reviewStatus,
                          List<Citation> citations, String internalClarification,
                          List<Finding> findings, long revision) {
        this(id, question, category, answer, answerStatus, reviewStatus, citations,
                internalClarification, findings, derivedClaims(answer, citations), revision);
    }

    public QuestionResult(String id, String question, String category, String answer,
                          AnswerStatus answerStatus, ReviewStatus reviewStatus,
                          List<Citation> citations, String internalClarification,
                          List<Finding> findings, List<AnswerClaim> claims) {
        this(id, question, category, answer, answerStatus, reviewStatus, citations,
                internalClarification, findings, claims, 0);
    }

    public QuestionResult {
        citations = citations == null ? List.of() : List.copyOf(citations);
        findings = findings == null ? List.of() : List.copyOf(findings);
        claims = claims == null ? List.of() : List.copyOf(claims);
        if (revision < 0) throw new IllegalArgumentException("revision darf nicht negativ sein");
    }

    public QuestionResult update(String newAnswer, ReviewStatus newReviewStatus) {
        boolean approvedAnswerChanged = reviewStatus == ReviewStatus.APPROVED
                && newAnswer != null && !Objects.equals(answer, newAnswer);
        String updatedAnswer = newAnswer == null ? answer : newAnswer;
        ReviewStatus updatedReviewStatus = approvedAnswerChanged ? ReviewStatus.DRAFT
                : newReviewStatus == null ? reviewStatus : newReviewStatus;
        if (Objects.equals(answer, updatedAnswer) && reviewStatus == updatedReviewStatus) return this;
        return new QuestionResult(id, question, category, updatedAnswer,
                answerStatus,
                updatedReviewStatus, citations, internalClarification, findings, claims, revision + 1);
    }

    private static List<AnswerClaim> derivedClaims(String answer, List<Citation> citations) {
        if (answer == null || answer.isBlank() || citations == null || citations.isEmpty()) return List.of();
        return List.of(new AnswerClaim(answer, citations.stream().map(Citation::chunkId).distinct().toList()));
    }
}
