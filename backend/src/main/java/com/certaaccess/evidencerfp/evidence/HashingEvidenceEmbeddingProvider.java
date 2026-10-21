package com.certaaccess.evidencerfp.evidence;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministischer Offline-Fallback für den Production-Stack ohne aktiviertes KI-Profil.
 * Mit dem Profil {@code ollama} übernimmt stattdessen bge-m3 die Einbettung.
 */
@Component
@Profile("production & !ollama & !openai")
public class HashingEvidenceEmbeddingProvider implements EvidenceEmbeddingProvider {
    @Override
    public String modelId() {
        return "hashing-1024-v2";
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String term : EvidenceTextTerms.tokens(text)) {
            byte[] digest = sha256(term);
            int index = ((digest[0] & 0xff) << 8 | (digest[1] & 0xff)) % DIMENSIONS;
            float sign = (digest[2] & 1) == 0 ? 1f : -1f;
            vector[index] += sign;

            // Ein zweiter unabhängiger Feature-Bucket reduziert einzelne Hash-Kollisionen,
            // ohne die feste 1.024-dimensionale Offline-Schnittstelle zu verändern.
            int secondaryIndex = ((digest[3] & 0xff) << 8 | (digest[4] & 0xff)) % DIMENSIONS;
            float secondarySign = (digest[5] & 1) == 0 ? 0.5f : -0.5f;
            vector[secondaryIndex] += secondarySign;
        }
        double sum = 0;
        for (float value : vector) sum += value * value;
        double norm = Math.sqrt(sum);
        if (norm == 0) return vector;
        for (int index = 0; index < vector.length; index++) vector[index] /= (float) norm;
        return vector;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 ist nicht verfügbar.", exception);
        }
    }
}
