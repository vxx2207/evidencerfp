package com.certaaccess.evidencerfp.analysis.domain;

import java.util.UUID;

/** Vollständiger, serverseitig extrahierter Chunk für die menschliche Quellenprüfung. */
public record EvidencePassage(
        UUID chunkId,
        String locator,
        String content
) {
}
