package com.certaaccess.evidencerfp.evidence;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Lokale semantische Einbettung über das in application-ollama.yaml konfigurierte bge-m3-Modell.
 */
@Component
@Profile("production & ollama")
public class OllamaEvidenceEmbeddingProvider implements EvidenceEmbeddingProvider {
    private final EmbeddingModel embeddingModel;

    public OllamaEvidenceEmbeddingProvider(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = embeddingModel.embed(text);
        if (vector.length != DIMENSIONS) {
            throw new IllegalStateException("bge-m3 muss 1024 Dimensionen liefern, erhalten: " + vector.length);
        }
        return vector;
    }
}
