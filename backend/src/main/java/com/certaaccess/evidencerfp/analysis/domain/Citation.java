package com.certaaccess.evidencerfp.analysis.domain;

import com.certaaccess.evidencerfp.evidence.RetrievalProvenance;

import java.util.UUID;

public record Citation(
        UUID chunkId,
        String documentName,
        String locator,
        String quote,
        RetrievalProvenance retrievalProvenance
) {
    public Citation(UUID chunkId, String documentName, String locator, String quote) {
        this(chunkId, documentName, locator, quote, null);
    }
}
