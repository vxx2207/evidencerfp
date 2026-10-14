package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.analysis.domain.EvidenceDocument;

import java.util.List;

public record ExtractedEvidence(EvidenceDocument document, List<EvidenceChunk> chunks) {
    public ExtractedEvidence {
        chunks = List.copyOf(chunks);
    }
}
