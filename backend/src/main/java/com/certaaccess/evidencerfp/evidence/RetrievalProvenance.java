package com.certaaccess.evidencerfp.evidence;

import java.util.Set;

/**
 * Nachvollziehbare Retrieval-Metadaten. Der Score beschreibt ausschließlich die Rangfusion und
 * ist ausdrücklich keine KI-Konfidenz.
 */
public record RetrievalProvenance(
        Set<RetrievalChannel> channels,
        Integer denseRank,
        Integer fullTextRank,
        double fusedScore,
        String retrievalVersion,
        String embeddingModel,
        String chunkSha256
) {
    public RetrievalProvenance {
        channels = channels == null ? Set.of() : Set.copyOf(channels);
        if (denseRank != null && denseRank < 1) throw new IllegalArgumentException("denseRank muss positiv sein.");
        if (fullTextRank != null && fullTextRank < 1) {
            throw new IllegalArgumentException("fullTextRank muss positiv sein.");
        }
        if (!Double.isFinite(fusedScore) || fusedScore < 0) {
            throw new IllegalArgumentException("fusedScore muss endlich und nicht negativ sein.");
        }
    }
}
