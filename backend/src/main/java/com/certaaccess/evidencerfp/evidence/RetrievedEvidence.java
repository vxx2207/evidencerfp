package com.certaaccess.evidencerfp.evidence;

import java.util.Objects;

public record RetrievedEvidence(EvidenceChunk chunk, RetrievalProvenance provenance) {
    public RetrievedEvidence {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(provenance, "provenance");
    }
}
