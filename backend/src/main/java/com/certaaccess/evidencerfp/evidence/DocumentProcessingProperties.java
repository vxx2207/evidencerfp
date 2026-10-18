package com.certaaccess.evidencerfp.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Harte Ressourcenbudgets fuer nicht vertrauenswuerdige Kundendokumente.
 *
 * <p>Die Defaults sind absichtlich auch am Binding-Typ hinterlegt. Damit gelten dieselben
 * Sicherheitsgrenzen, wenn ein Profil einzelne Werte nicht erneut auffuehrt.</p>
 */
@ConfigurationProperties("evidence-rfp.documents")
public record DocumentProcessingProperties(
        @DefaultValue("365") int staleAfterDays,
        @DefaultValue("200") int maxPdfPages,
        @DefaultValue("1000000") int maxExtractedCharacters,
        @DefaultValue("2000") int maxChunksPerFile,
        @DefaultValue("104857600") long maxOoxmlExpandedBytes,
        @DefaultValue("100") double maxOoxmlCompressionRatio,
        @DefaultValue("2000") int maxOoxmlEntries,
        @DefaultValue("4000") int maxQuestionCharacters,
        @DefaultValue("20s") Duration parserTimeout
) {
    public DocumentProcessingProperties {
        if (staleAfterDays < 1 || maxPdfPages < 1 || maxExtractedCharacters < 1
                || maxExtractedCharacters == Integer.MAX_VALUE
                || maxChunksPerFile < 1 || maxOoxmlExpandedBytes < 1
                || maxOoxmlCompressionRatio < 1 || maxOoxmlEntries < 1
                || maxQuestionCharacters < 1 || parserTimeout == null
                || parserTimeout.isZero() || parserTimeout.isNegative()) {
            throw new IllegalArgumentException("Dokumentenbudgets muessen positive Werte enthalten.");
        }
    }

    static DocumentProcessingProperties secureDefaults() {
        return new DocumentProcessingProperties(365, 200, 1_000_000, 2_000,
                100L * 1024 * 1024, 100.0, 2_000, 4_000, Duration.ofSeconds(20));
    }
}
