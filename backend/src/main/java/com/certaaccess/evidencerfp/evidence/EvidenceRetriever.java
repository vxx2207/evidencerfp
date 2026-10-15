package com.certaaccess.evidencerfp.evidence;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Port für Retrieval; Implementierungen dürfen lokal oder über pgvector suchen. */
public interface EvidenceRetriever {
    List<EvidenceChunk> retrieve(RfpQuestion question, List<EvidenceChunk> availableChunks, int limit);

    /**
     * Ruft Evidenz für einen kompletten Fragebogen ab. Implementierungen mit einem externen Index
     * können den Korpus dabei einmalig vorbereiten und für alle Querys wiederverwenden.
     */
    default Map<String, List<EvidenceChunk>> retrieveBatch(List<RfpQuestion> questions,
                                                           List<EvidenceChunk> availableChunks,
                                                           int limit) {
        Map<String, List<EvidenceChunk>> results = new LinkedHashMap<>();
        for (RfpQuestion question : questions) {
            results.put(question.id(), retrieve(question, availableChunks, limit));
        }
        return results;
    }
}
