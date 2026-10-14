package com.certaaccess.evidencerfp.evidence;

import java.util.List;

/** Port für Retrieval; Implementierungen dürfen lokal oder über pgvector suchen. */
public interface EvidenceRetriever {
    List<EvidenceChunk> retrieve(RfpQuestion question, List<EvidenceChunk> availableChunks, int limit);
}
