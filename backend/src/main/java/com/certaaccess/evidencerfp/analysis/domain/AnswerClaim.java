package com.certaaccess.evidencerfp.analysis.domain;

import java.util.List;
import java.util.UUID;

/** Eine atomare, bereits verifizierte Aussage und die dafür zulässigen Evidenz-Chunks. */
public record AnswerClaim(String text, List<UUID> citationChunkIds) {
    public AnswerClaim {
        text = text == null ? "" : text.strip();
        citationChunkIds = citationChunkIds == null ? List.of() : List.copyOf(citationChunkIds);
        if (text.isBlank()) throw new IllegalArgumentException("Claim-Text darf nicht leer sein.");
        if (citationChunkIds.isEmpty()) {
            throw new IllegalArgumentException("Ein verifizierter Claim benötigt mindestens eine Zitation.");
        }
    }
}
