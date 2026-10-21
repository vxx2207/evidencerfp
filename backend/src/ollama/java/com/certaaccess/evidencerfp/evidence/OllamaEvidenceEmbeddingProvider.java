package com.certaaccess.evidencerfp.evidence;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.List;

/**
 * Lokale semantische Einbettung über das in application-ollama.yaml konfigurierte bge-m3-Modell.
 */
@Component
@Profile("production & ollama")
public class OllamaEvidenceEmbeddingProvider implements EvidenceEmbeddingProvider {
    private final EmbeddingModel embeddingModel;
    private final Duration timeout;
    private final int maxRetries;
    private final String modelId;

    public OllamaEvidenceEmbeddingProvider(EmbeddingModel embeddingModel,
            @Value("${evidence-rfp.ai.embedding-timeout-seconds:30}") int timeoutSeconds,
            @Value("${evidence-rfp.ai.max-retries:2}") int maxRetries,
            @Value("${evidence-rfp.ai.embedding-model:bge-m3}") String modelId) {
        this.embeddingModel = embeddingModel;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.maxRetries = Math.min(2, Math.max(0, maxRetries));
        this.modelId = modelId;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = EmbeddingProviderSupport.call(() -> embeddingModel.embed(text), timeout, maxRetries);
        validate(vector);
        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> vectors = EmbeddingProviderSupport.call(
                () -> embeddingModel.embed(texts), timeout, maxRetries);
        if (vectors.size() != texts.size()) {
            throw new EvidenceProviderException("EMBEDDING_BATCH_INCOMPLETE",
                    "Der Embedding-Provider lieferte eine unvollständige Batch-Antwort.", null);
        }
        vectors.forEach(this::validate);
        return vectors;
    }

    private void validate(float[] vector) {
        if (vector.length != DIMENSIONS) {
            throw new IllegalStateException("bge-m3 muss 1024 Dimensionen liefern, erhalten: " + vector.length);
        }
    }

    @Override
    public String modelId() {
        return modelId;
    }
}
