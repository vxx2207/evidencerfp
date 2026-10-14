package com.certaaccess.evidencerfp.evidence;

/**
 * Erzeugt die 1.024-dimensionalen Vektoren, die der pgvector-Index erwartet.
 */
public interface EvidenceEmbeddingProvider {
    int DIMENSIONS = 1_024;

    float[] embed(String text);
}
