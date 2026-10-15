package com.certaaccess.evidencerfp.evidence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Produktions-Retriever, der Chunks tatsächlich über den pgvector-Operator {@code <=>} ordnet.
 * Der Vektor-Provider wird profilabhängig gewählt: deterministisch offline oder über bge-m3.
 */
@Component
@Profile("production")
public class PgVectorEvidenceRetriever implements EvidenceRetriever {
    private final JdbcClient jdbc;
    private final EvidenceEmbeddingProvider embeddingProvider;

    public PgVectorEvidenceRetriever(JdbcClient jdbc, EvidenceEmbeddingProvider embeddingProvider) {
        this.jdbc = jdbc;
        this.embeddingProvider = embeddingProvider;
    }

    @Override
    public List<EvidenceChunk> retrieve(RfpQuestion question, List<EvidenceChunk> availableChunks, int limit) {
        return retrieveBatch(List.of(question), availableChunks, limit)
                .getOrDefault(question.id(), List.of());
    }

    @Override
    @Transactional
    public Map<String, List<EvidenceChunk>> retrieveBatch(List<RfpQuestion> questions,
                                                          List<EvidenceChunk> availableChunks,
                                                          int limit) {
        Map<String, List<EvidenceChunk>> results = new LinkedHashMap<>();
        if (questions.isEmpty()) return results;
        if (availableChunks.isEmpty()) {
            questions.forEach(question -> results.put(question.id(), List.of()));
            return results;
        }
        UUID sessionId = UUID.randomUUID();
        try {
            jdbc.sql("DELETE FROM evidence_retrieval_chunk WHERE expires_at < now()").update();
            for (EvidenceChunk chunk : availableChunks) {
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
                        .param("documentDate", chunk.documentDate() == null ? null : Date.valueOf(chunk.documentDate()))
                        .param("embedding", vectorLiteral(embeddingProvider.embed(chunk.content())))
                        .update();
            }
            for (RfpQuestion question : questions) {
                List<EvidenceChunk> matches = jdbc.sql("""
                        SELECT chunk_id, document_name, media_type, locator, content, document_date
                        FROM evidence_retrieval_chunk
                        WHERE session_id = :sessionId
                        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
                        LIMIT :resultLimit
                        """)
                        .param("sessionId", sessionId)
                        .param("queryEmbedding", vectorLiteral(embeddingProvider.embed(question.question())))
                        .param("resultLimit", Math.max(1, limit))
                        .query((row, rowNumber) -> new EvidenceChunk(
                                row.getObject("chunk_id", UUID.class),
                                row.getString("document_name"), row.getString("media_type"),
                                row.getString("locator"), row.getString("content"),
                                row.getObject("document_date", java.time.LocalDate.class)))
                        .list();
                results.put(question.id(), matches);
            }
            return results;
        } finally {
            jdbc.sql("DELETE FROM evidence_retrieval_chunk WHERE session_id = :sessionId")
                    .param("sessionId", sessionId).update();
        }
    }

    private String vectorLiteral(float[] values) {
        if (values.length != EvidenceEmbeddingProvider.DIMENSIONS) {
            throw new IllegalArgumentException("Der Embedding-Provider muss 1024 Dimensionen liefern.");
        }
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) literal.append(',');
            literal.append(values[index]);
        }
        return literal.append(']').toString();
    }
}
