package com.certaaccess.evidencerfp.evidence;

import java.time.LocalDate;
import java.util.UUID;

public record EvidenceChunk(
        UUID id,
        String documentName,
        String mediaType,
        String locator,
        String content,
        LocalDate documentDate
) {
}
