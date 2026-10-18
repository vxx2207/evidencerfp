package com.certaaccess.evidencerfp.evidence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hybrides Production-Retrieval: Dense Search und deutsche PostgreSQL-FTS liefern je zwölf
 * Kandidaten. Reciprocal Rank Fusion (k=60) erzeugt daraus höchstens sechs Ergebnisse.
 */
@Component
@Profile("production")
public class PgVectorEvidenceRetriever implements EvidenceRetriever {
    public static final String RETRIEVAL_VERSION = "hybrid-pgvector-fts-rrf-v1";
    static final int CANDIDATE_LIMIT = 12;
    static final int FINAL_LIMIT = 6;
    static final int RRF_K = 60;
    static final int EMBEDDING_BATCH_SIZE = 32;

    private final JdbcClient jdbc;
    private final EvidenceEmbeddingProvider embeddingProvider;

    public PgVectorEvidenceRetriever(JdbcClient jdbc, EvidenceEmbeddingProvider embeddingProvider) {
        this.jdbc = jdbc;
        this.embeddingProvider = embeddingProvider;
    }

    @Override
    public List<EvidenceChunk> retrieve(RfpQuestion question, List<EvidenceChunk> availableChunks, int limit) {
        return retrieveWithProvenance(question, availableChunks, limit).chunks();
    }

    @Override
    public RetrievalResult retrieveWithProvenance(RfpQuestion question,
                                                   List<EvidenceChunk> availableChunks,
                                                   int limit) {
        return retrieveBatchWithProvenance(List.of(question), availableChunks, limit)
                .getOrDefault(question.id(), new RetrievalResult(question.id(), List.of()));
    }

    @Override
    public Map<String, List<EvidenceChunk>> retrieveBatch(List<RfpQuestion> questions,
                                                          List<EvidenceChunk> availableChunks,
                                                          int limit) {
        Map<String, List<EvidenceChunk>> results = new LinkedHashMap<>();
        retrieveBatchWithProvenance(questions, availableChunks, limit)
                .forEach((id, result) -> results.put(id, result.chunks()));
        return results;
    }

    @Override
    public Map<String, RetrievalResult> retrieveBatchWithProvenance(List<RfpQuestion> questions,
                                                                    List<EvidenceChunk> availableChunks,
                                                                    int limit) {
        Map<String, RetrievalResult> results = new LinkedHashMap<>();
        if (questions.isEmpty()) return results;
        if (availableChunks.isEmpty()) {
            questions.forEach(question -> results.put(question.id(),
                    new RetrievalResult(question.id(), List.of())));
            return results;
        }

        // Provideraufrufe laufen bewusst vor allen Datenbank-Schreibvorgängen: Kein Netzwerk-
        // oder Modell-Request hält eine offene DB-Transaktion.
        List<float[]> chunkEmbeddings = embedBatches(
                availableChunks.stream().map(this::searchableChunkText).toList());
        List<float[]> queryEmbeddings = embedBatches(
                questions.stream().map(this::searchableQuestionText).toList());
        UUID sessionId = UUID.randomUUID();
        try {
            jdbc.sql("DELETE FROM evidence_retrieval_chunk WHERE expires_at < now()").update();
            insertCorpus(sessionId, availableChunks, chunkEmbeddings);
            int resultLimit = Math.min(FINAL_LIMIT, Math.max(1, limit));
            for (int index = 0; index < questions.size(); index++) {
                RfpQuestion question = questions.get(index);
                results.put(question.id(), new RetrievalResult(question.id(),
                        hybridQuery(sessionId, question, queryEmbeddings.get(index), resultLimit)));
            }
            return results;
        } finally {
            jdbc.sql("DELETE FROM evidence_retrieval_chunk WHERE session_id = :sessionId")
                    .param("sessionId", sessionId).update();
        }
    }

    private List<float[]> embedBatches(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(texts.size(), start + EMBEDDING_BATCH_SIZE);
            List<float[]> batch = embeddingProvider.embedBatch(texts.subList(start, end));
            if (batch.size() != end - start) {
                throw new IllegalStateException("Der Embedding-Provider lieferte eine unvollständige Batch-Antwort.");
            }
            batch.forEach(this::validateEmbedding);
            result.addAll(batch);
        }
        return result;
    }

    private void insertCorpus(UUID sessionId, List<EvidenceChunk> chunks, List<float[]> embeddings) {
        for (int index = 0; index < chunks.size(); index++) {
            EvidenceChunk chunk = chunks.get(index);
            jdbc.sql("""
                    INSERT INTO evidence_retrieval_chunk
                      (session_id, chunk_id, document_name, media_type, locator, content, document_date, embedding)
                    VALUES (:sessionId, :chunkId, :documentName, :mediaType, :locator, :content,
                            :documentDate, CAST(:embedding AS vector))
                    """)
                    .param("sessionId", sessionId)
                    .param("chunkId", chunk.id())
                    .param("documentName", chunk.documentName())
                    .param("mediaType", chunk.mediaType())
                    .param("locator", chunk.locator())
                    .param("content", chunk.content())
                    .param("documentDate", chunk.documentDate() == null
                            ? null : Date.valueOf(chunk.documentDate()))
                    .param("embedding", vectorLiteral(embeddings.get(index)))
                    .update();
        }
    }

    private List<RetrievedEvidence> hybridQuery(UUID sessionId, RfpQuestion question,
                                                float[] queryEmbedding, int resultLimit) {
        return jdbc.sql("""
                WITH dense_candidates AS MATERIALIZED (
                    SELECT chunk_id,
                           row_number() OVER (ORDER BY embedding <=> CAST(:queryEmbedding AS vector), chunk_id)::int
                               AS dense_rank
                    FROM evidence_retrieval_chunk
                    WHERE session_id = :sessionId
                    ORDER BY embedding <=> CAST(:queryEmbedding AS vector), chunk_id
                    LIMIT :candidateLimit
                ), query_lexemes AS (
                    SELECT tsvector_to_array(to_tsvector('german', :queryText)) AS lexemes
                ), query AS (
                    SELECT CASE
                             WHEN cardinality(lexemes) = 0 THEN NULL::tsquery
                             ELSE to_tsquery('german', array_to_string(lexemes, ' | '))
                           END AS terms
                    FROM query_lexemes
                ), full_text_candidates AS MATERIALIZED (
                    SELECT chunk_id,
                           row_number() OVER (
                               ORDER BY ts_rank_cd(content_tsv, query.terms) DESC, chunk_id)::int
                               AS full_text_rank
                    FROM evidence_retrieval_chunk, query
                    WHERE session_id = :sessionId
                      AND query.terms IS NOT NULL
                      AND content_tsv @@ query.terms
                    ORDER BY ts_rank_cd(content_tsv, query.terms) DESC, chunk_id
                    LIMIT :candidateLimit
                ), fused AS (
                    SELECT COALESCE(d.chunk_id, f.chunk_id) AS chunk_id,
                           d.dense_rank,
                           f.full_text_rank,
                           COALESCE(1.0 / (:rrfK + d.dense_rank), 0.0)
                             + COALESCE(1.0 / (:rrfK + f.full_text_rank), 0.0) AS fused_score
                    FROM dense_candidates d
                    FULL OUTER JOIN full_text_candidates f USING (chunk_id)
                )
                SELECT c.chunk_id, c.document_name, c.media_type, c.locator, c.content,
                       c.document_date, fused.dense_rank, fused.full_text_rank, fused.fused_score
                FROM fused
                JOIN evidence_retrieval_chunk c
                  ON c.session_id = :sessionId AND c.chunk_id = fused.chunk_id
                ORDER BY fused.fused_score DESC,
                         COALESCE(fused.dense_rank, 2147483647),
                         COALESCE(fused.full_text_rank, 2147483647), c.chunk_id
                LIMIT :resultLimit
                """)
                .param("sessionId", sessionId)
                .param("queryEmbedding", vectorLiteral(queryEmbedding))
                .param("queryText", searchableQuestionText(question))
                .param("candidateLimit", CANDIDATE_LIMIT)
                .param("rrfK", RRF_K)
                .param("resultLimit", resultLimit)
                .query((row, rowNumber) -> {
                    EvidenceChunk chunk = new EvidenceChunk(
                            row.getObject("chunk_id", UUID.class),
                            row.getString("document_name"), row.getString("media_type"),
                            row.getString("locator"), row.getString("content"),
                            row.getObject("document_date", java.time.LocalDate.class));
                    Integer denseRank = row.getObject("dense_rank", Integer.class);
                    Integer fullTextRank = row.getObject("full_text_rank", Integer.class);
                    Set<RetrievalChannel> channels = new LinkedHashSet<>();
                    if (denseRank != null) channels.add(RetrievalChannel.DENSE);
                    if (fullTextRank != null) channels.add(RetrievalChannel.FULL_TEXT);
                    return new RetrievedEvidence(chunk, new RetrievalProvenance(
                            channels, denseRank, fullTextRank, row.getDouble("fused_score"),
                            RETRIEVAL_VERSION, embeddingProvider.modelId(),
                            EvidenceChunkIdentity.sha256(chunk.content())));
                })
                .list();
    }

    private String searchableChunkText(EvidenceChunk chunk) {
        return chunk.locator() + "\n" + chunk.content();
    }

    private String searchableQuestionText(RfpQuestion question) {
        if (question.category() == null || question.category().isBlank()) return question.question();
        return question.category() + "\n" + question.question();
    }

    private void validateEmbedding(float[] values) {
        if (values == null || values.length != EvidenceEmbeddingProvider.DIMENSIONS) {
            throw new IllegalArgumentException("Der Embedding-Provider muss 1024 Dimensionen liefern.");
        }
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Embeddings dürfen keine nicht-endlichen Werte enthalten.");
            }
        }
    }

    private String vectorLiteral(float[] values) {
        validateEmbedding(values);
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) literal.append(',');
            literal.append(values[index]);
        }
        return literal.append(']').toString();
    }
}
