package com.certaaccess.evidencerfp.analysis.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Unveränderlicher Nachweis eines generierten oder menschlich geprüften Antwortstands. */
public record ReviewEvent(
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
    public ReviewEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(questionId, "questionId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(afterReviewStatus, "afterReviewStatus");
    }

    static ReviewEvent generated(UUID analysisId, QuestionResult question, Instant occurredAt) {
        UUID eventId = UUID.nameUUIDFromBytes(
                (analysisId + ":" + question.id() + ":GENERATED").getBytes(StandardCharsets.UTF_8));
        return new ReviewEvent(eventId, question.id(), ReviewActor.ANALYSIS_ENGINE,
                ReviewAction.GENERATED, ReviewProvenance.GENERATED, occurredAt,
                null, question.answer(), null, question.reviewStatus());
    }

    static ReviewEvent humanChange(QuestionResult before, QuestionResult after, Instant occurredAt) {
        boolean answerChanged = !Objects.equals(before.answer(), after.answer());
        boolean statusChanged = before.reviewStatus() != after.reviewStatus();
        ReviewAction action = answerChanged && statusChanged
                ? ReviewAction.ANSWER_AND_REVIEW_STATUS_CHANGED
                : answerChanged ? ReviewAction.ANSWER_EDITED : ReviewAction.REVIEW_STATUS_CHANGED;
        return new ReviewEvent(UUID.randomUUID(), before.id(), ReviewActor.LOCAL_REVIEWER,
                action, ReviewProvenance.HUMAN_EDITED, occurredAt,
                before.answer(), after.answer(), before.reviewStatus(), after.reviewStatus());
    }
}
