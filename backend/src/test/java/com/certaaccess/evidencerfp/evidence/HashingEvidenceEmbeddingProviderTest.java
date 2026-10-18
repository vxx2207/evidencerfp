package com.certaaccess.evidencerfp.evidence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashingEvidenceEmbeddingProviderTest {
    private final HashingEvidenceEmbeddingProvider provider = new HashingEvidenceEmbeddingProvider();

    @Test
    void mapsDomainParaphrasesCloserThanUnrelatedText() {
        float[] question = provider.embed("Wie werden ruhende Kundendaten verschlüsselt?");
        float[] evidence = provider.embed("Persistente Inhalte sind im Ruhezustand mit AES-256 geschützt.");
        float[] unrelated = provider.embed("Audit-Ereignisse werden an ein SIEM exportiert.");

        assertThat(cosine(question, evidence)).isGreaterThan(cosine(question, unrelated));
        assertThat(provider.modelId()).isEqualTo("hashing-1024-v2");
    }

    private double cosine(float[] left, float[] right) {
        double value = 0;
        for (int index = 0; index < left.length; index++) value += left[index] * right[index];
        return value;
    }
}
