package com.certaaccess.evidencerfp.evidence;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Explizit aktivierbarer Cloud-Adapter. Die konfigurierte Dimension muss zum pgvector-Schema passen.
 */
@Component
@Profile("production & openai")
public class OpenAiEvidenceEmbeddingProvider implements EvidenceEmbeddingProvider {
    private final EmbeddingModel embeddingModel;

    public OpenAiEvidenceEmbeddingProvider(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = embeddingModel.embed(text);
        if (vector.length != DIMENSIONS) {
            throw new IllegalStateException("Das Cloud-Embedding muss 1024 Dimensionen liefern, erhalten: "
                    + vector.length);
        }
        return vector;
    }
}
