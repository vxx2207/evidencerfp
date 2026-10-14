package com.certaaccess.evidencerfp.evidence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LexicalEvidenceRetrieverTest {
    private final LexicalEvidenceRetriever retriever = new LexicalEvidenceRetriever();

    @Test
    void ranksChunkWithMostQuestionTermsFirstAndHonorsLimit() {
        EvidenceChunk unrelated = chunk("Backups werden täglich erstellt.");
        EvidenceChunk relevant = chunk("Privilegierte Konten erzwingen FIDO2 als phishing-resistente MFA.");
        EvidenceChunk partial = chunk("FIDO2 wird für Benutzer unterstützt.");

        List<EvidenceChunk> result = retriever.retrieve(
                new RfpQuestion("1", "Wird FIDO2 für privilegierte Konten erzwungen?", "IAM"),
                List.of(unrelated, partial, relevant), 2);

        assertThat(result).containsExactly(relevant, partial);
    }

    private EvidenceChunk chunk(String content) {
        return new EvidenceChunk(UUID.randomUUID(), "beleg.pdf", "application/pdf", "Seite 1", content, null);
    }
}
