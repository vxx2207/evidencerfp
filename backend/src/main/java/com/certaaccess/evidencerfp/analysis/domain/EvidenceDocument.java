package com.certaaccess.evidencerfp.analysis.domain;

import java.time.LocalDate;
import java.util.List;

public record EvidenceDocument(
        String name,
        String mediaType,
        int chunkCount,
        LocalDate documentDate,
        boolean stale,
        List<EvidencePassage> passages
) {
    public EvidenceDocument {
        passages = passages == null ? List.of() : List.copyOf(passages);
    }

    public EvidenceDocument(String name, String mediaType, int chunkCount,
                            LocalDate documentDate, boolean stale) {
        this(name, mediaType, chunkCount, documentDate, stale, List.of());
    }
}
