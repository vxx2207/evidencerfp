package com.certaaccess.evidencerfp.analysis.domain;

import java.util.Objects;

/**
 * Unveränderlicher technischer Fingerabdruck des Analyse-Laufs.
 *
 * <p>Die Werte gehören zum Ergebnis und werden deshalb zusammen mit der Analyse
 * persistiert. Sie dürfen beim Lesen nicht aus der dann aktiven Konfiguration
 * rekonstruiert werden.</p>
 */
public record AnalysisRunMetadata(
        String retrievalVersion,
        String promptVersion,
        String embeddingModel,
        String chatModel
) {
    public static final String RETRIEVAL_VERSION = "hybrid-pgvector-fts-rrf-v1";
    public static final String DETERMINISTIC_RETRIEVAL_VERSION = "lexical-deterministic-v2";
    public static final String PRECOMPUTED_DEMO_VERSION = "precomputed-demo-v1";
    public static final String PROMPT_VERSION = "claims-v1";
    public static final String DETERMINISTIC_EMBEDDING_MODEL = "none";
    public static final String DETERMINISTIC_CHAT_MODEL = "deterministic-answer-v2";
    public static final String PRECOMPUTED_MODEL = "precomputed-synthetic-fixture";

    public AnalysisRunMetadata {
        retrievalVersion = requireValue(retrievalVersion, "retrievalVersion");
        promptVersion = requireValue(promptVersion, "promptVersion");
        embeddingModel = requireValue(embeddingModel, "embeddingModel");
        chatModel = requireValue(chatModel, "chatModel");
    }

    public static AnalysisRunMetadata deterministicDefaults() {
        return new AnalysisRunMetadata(DETERMINISTIC_RETRIEVAL_VERSION, PROMPT_VERSION,
                DETERMINISTIC_EMBEDDING_MODEL, DETERMINISTIC_CHAT_MODEL);
    }

    public static AnalysisRunMetadata precomputedDemo() {
        return new AnalysisRunMetadata(PRECOMPUTED_DEMO_VERSION, PROMPT_VERSION,
                PRECOMPUTED_MODEL, PRECOMPUTED_MODEL);
    }

    private static String requireValue(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " darf nicht leer sein.");
        return normalized;
    }
}
