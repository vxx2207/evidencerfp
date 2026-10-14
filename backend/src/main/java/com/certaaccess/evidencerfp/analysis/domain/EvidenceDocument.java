package com.certaaccess.evidencerfp.analysis.domain;

import java.time.LocalDate;

public record EvidenceDocument(
        String name,
        String mediaType,
        int chunkCount,
        LocalDate documentDate,
        boolean stale
) {
}
