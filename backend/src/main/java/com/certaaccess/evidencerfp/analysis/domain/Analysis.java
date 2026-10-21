package com.certaaccess.evidencerfp.analysis.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Analysis {
    private final UUID id;
    private final String customerName;
    private final boolean synthetic;
    private final Instant createdAt;
    private final Instant completedAt;
    private final Map<String, QuestionResult> questions;
    private final List<Finding> findings;
    private final List<EvidenceDocument> evidenceDocuments;
    private final List<ReviewEvent> reviewEvents;
    private final AnalysisStatus status;
    private final AnalysisRunMetadata runMetadata;

    public Analysis(UUID id, String customerName, boolean synthetic, AnalysisStatus status,
                    Instant createdAt, Instant completedAt, List<QuestionResult> questions,
                    List<Finding> findings, List<EvidenceDocument> evidenceDocuments) {
        this(id, customerName, synthetic, status, createdAt, completedAt, questions, findings,
                evidenceDocuments, initialReviewEvents(id, status, createdAt, completedAt, questions),
                AnalysisRunMetadata.deterministicDefaults());
    }

    public Analysis(UUID id, String customerName, boolean synthetic, AnalysisStatus status,
                    Instant createdAt, Instant completedAt, List<QuestionResult> questions,
                    List<Finding> findings, List<EvidenceDocument> evidenceDocuments,
                    List<ReviewEvent> reviewEvents) {
        this(id, customerName, synthetic, status, createdAt, completedAt, questions, findings,
                evidenceDocuments, reviewEvents, AnalysisRunMetadata.deterministicDefaults());
    }

    public Analysis(UUID id, String customerName, boolean synthetic, AnalysisStatus status,
                    Instant createdAt, Instant completedAt, List<QuestionResult> questions,
                    List<Finding> findings, List<EvidenceDocument> evidenceDocuments,
                    AnalysisRunMetadata runMetadata) {
        this(id, customerName, synthetic, status, createdAt, completedAt, questions, findings,
                evidenceDocuments, initialReviewEvents(id, status, createdAt, completedAt, questions),
                runMetadata);
    }

    public Analysis(UUID id, String customerName, boolean synthetic, AnalysisStatus status,
                    Instant createdAt, Instant completedAt, List<QuestionResult> questions,
                    List<Finding> findings, List<EvidenceDocument> evidenceDocuments,
                    List<ReviewEvent> reviewEvents, AnalysisRunMetadata runMetadata) {
        this.id = id;
        this.customerName = customerName;
        this.synthetic = synthetic;
        this.status = status;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.questions = new LinkedHashMap<>();
        questions.forEach(question -> this.questions.put(question.id(), question));
        this.findings = List.copyOf(findings);
        this.evidenceDocuments = List.copyOf(evidenceDocuments);
        this.reviewEvents = new ArrayList<>(reviewEvents);
        this.runMetadata = java.util.Objects.requireNonNull(runMetadata, "runMetadata");
    }

    public UUID id() { return id; }
    public String customerName() { return customerName; }
    public boolean synthetic() { return synthetic; }
    public AnalysisStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant completedAt() { return completedAt; }
    public synchronized List<QuestionResult> questions() { return new ArrayList<>(questions.values()); }
    public List<Finding> findings() { return findings; }
    public List<EvidenceDocument> evidenceDocuments() { return evidenceDocuments; }
    public synchronized List<ReviewEvent> reviewEvents() { return List.copyOf(reviewEvents); }
    public AnalysisRunMetadata runMetadata() { return runMetadata; }

    public synchronized boolean reviewComplete() {
        return status == AnalysisStatus.COMPLETED && !questions.isEmpty()
                && questions.values().stream()
                .allMatch(question -> question.reviewStatus() != ReviewStatus.DRAFT);
    }

    public synchronized void requireCompleteReview() {
        if (status != AnalysisStatus.COMPLETED) {
            throw new ReviewIncompleteException(
                    "Der Export ist erst nach Abschluss der Analyse möglich.");
        }
        long openQuestions = questions.values().stream()
                .filter(question -> question.reviewStatus() == ReviewStatus.DRAFT)
                .count();
        if (questions.isEmpty() || openQuestions > 0) {
            throw new ReviewIncompleteException("Der Export ist erst möglich, wenn jede Frage entschieden ist. "
                    + "Noch offene Fragen: " + openQuestions + ".");
        }
    }

    public synchronized QuestionResult updateQuestion(String questionId, String answer, ReviewStatus reviewStatus) {
        return updateQuestion(questionId, answer, reviewStatus, Instant.now());
    }

    public synchronized QuestionResult updateQuestion(String questionId, String answer, ReviewStatus reviewStatus,
                                                       Instant occurredAt) {
        QuestionResult current = questions.get(questionId);
        if (current == null) {
            throw new IllegalArgumentException("Frage wurde nicht gefunden: " + questionId);
        }
        if (reviewStatus == ReviewStatus.APPROVED
                && (current.answerStatus() == AnswerStatus.NOT_FOUND
                || current.findings().stream().anyMatch(Finding::blocksApproval))) {
            throw new ApprovalBlockedException(
                    "Die Frage kann wegen blockierender Findings nicht freigegeben werden.");
        }
        QuestionResult updated = current.update(answer, reviewStatus);
        if (!current.equals(updated)) {
            questions.put(questionId, updated);
            reviewEvents.add(ReviewEvent.humanChange(current, updated, occurredAt));
        }
        return updated;
    }

    private static List<ReviewEvent> initialReviewEvents(UUID analysisId, AnalysisStatus status,
                                                         Instant createdAt, Instant completedAt,
                                                         List<QuestionResult> questions) {
        if (status != AnalysisStatus.COMPLETED) return List.of();
        Instant occurredAt = completedAt == null ? createdAt : completedAt;
        return questions.stream()
                .map(question -> ReviewEvent.generated(analysisId, question, occurredAt))
                .toList();
    }
}
