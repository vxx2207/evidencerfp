package com.certaaccess.evidencerfp.evidence;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Deterministischer Offline-Fallback für den Production-Stack ohne aktiviertes KI-Profil.
 * Mit dem Profil {@code ollama} übernimmt stattdessen bge-m3 die Einbettung.
 */
@Component
@Profile("production & !ollama & !openai")
public class HashingEvidenceEmbeddingProvider implements EvidenceEmbeddingProvider {
    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        String[] terms = text.toLowerCase(Locale.GERMAN).split("[^\\p{L}\\p{N}]+");
        for (String term : terms) {
            if (term.length() < 3) continue;
            byte[] digest = sha256(term);
            int index = ((digest[0] & 0xff) << 8 | (digest[1] & 0xff)) % DIMENSIONS;
            float sign = (digest[2] & 1) == 0 ? 1f : -1f;
            vector[index] += sign;
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
