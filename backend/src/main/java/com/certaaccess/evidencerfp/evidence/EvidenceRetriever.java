package com.certaaccess.evidencerfp.evidence;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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

    default RetrievalResult retrieveWithProvenance(RfpQuestion question,
                                                    List<EvidenceChunk> availableChunks,
                                                    int limit) {
        List<RetrievedEvidence> matches = retrieve(question, availableChunks, limit).stream()
                .map(chunk -> new RetrievedEvidence(chunk, new RetrievalProvenance(
                        Set.of(RetrievalChannel.LEXICAL_FALLBACK), null, null, 0,
                        "lexical-deterministic-v2", "none", EvidenceChunkIdentity.sha256(chunk.content()))))
                .toList();
        return new RetrievalResult(question.id(), matches);
    }

    default Map<String, RetrievalResult> retrieveBatchWithProvenance(List<RfpQuestion> questions,
                                                                     List<EvidenceChunk> availableChunks,
                                                                     int limit) {
        Map<String, List<EvidenceChunk>> chunksByQuestion = retrieveBatch(questions, availableChunks, limit);
        Map<String, RetrievalResult> results = new LinkedHashMap<>();
        for (RfpQuestion question : questions) {
            List<RetrievedEvidence> matches = chunksByQuestion.getOrDefault(question.id(), List.of()).stream()
                    .map(chunk -> new RetrievedEvidence(chunk, new RetrievalProvenance(
                            Set.of(RetrievalChannel.LEXICAL_FALLBACK), null, null, 0,
                            "lexical-deterministic-v2", "none", EvidenceChunkIdentity.sha256(chunk.content()))))
                    .toList();
            results.put(question.id(), new RetrievalResult(question.id(), matches));
        }
        return results;
    }
}
