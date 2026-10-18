package com.certaaccess.evidencerfp.evidence;

import java.util.List;

/**
 * Erzeugt die 1.024-dimensionalen Vektoren, die der pgvector-Index erwartet.
 */
public interface EvidenceEmbeddingProvider {
    int DIMENSIONS = 1_024;

    float[] embed(String text);

    /**
     * Batch-Port für Provider, die mehrere Texte in einem Request einbetten können. Der Default
     * hält bestehende Provider kompatibel; produktive Adapter können ihn ohne Änderung des
     * Retrievers optimieren.
     */
    default List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    /** Stabile, nicht geheime Modellkennung für Provenienz und Evaluation. */
    default String modelId() {
        return getClass().getSimpleName();
    }
}
