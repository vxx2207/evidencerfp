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
    private final AnalysisStatus status;

    public Analysis(UUID id, String customerName, boolean synthetic, AnalysisStatus status,
                    Instant createdAt, Instant completedAt, List<QuestionResult> questions,
                    List<Finding> findings, List<EvidenceDocument> evidenceDocuments) {
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

    public synchronized QuestionResult updateQuestion(String questionId, String answer, ReviewStatus reviewStatus) {
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
        questions.put(questionId, updated);
        return updated;
    }
}
