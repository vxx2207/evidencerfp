package com.certaaccess.evidencerfp.evidence;

import java.util.List;

public record RetrievalResult(String questionId, List<RetrievedEvidence> matches) {
    public RetrievalResult {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }

    public List<EvidenceChunk> chunks() {
        return matches.stream().map(RetrievedEvidence::chunk).toList();
    }
}
