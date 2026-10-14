package com.certaaccess.evidencerfp.analysis.domain;

import java.util.UUID;

public record Citation(
        UUID chunkId,
        String documentName,
        String locator,
        String quote
) {
}
