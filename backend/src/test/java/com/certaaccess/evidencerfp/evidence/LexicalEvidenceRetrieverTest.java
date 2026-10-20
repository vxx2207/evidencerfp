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

    @Test
    void defaultProvenanceUsesTheRetrieverReportedVersion() {
        EvidenceChunk evidence = chunk("Phishing-resistente MFA ist verpflichtend.");
        EvidenceRetriever versioned = new EvidenceRetriever() {
            @Override
            public String version() {
                return "custom-retriever-v7";
            }

            @Override
            public List<EvidenceChunk> retrieve(RfpQuestion question,
                                                List<EvidenceChunk> availableChunks, int limit) {
                return availableChunks.stream().limit(limit).toList();
            }
        };
        RfpQuestion question = new RfpQuestion("1", "Ist MFA verpflichtend?", "IAM");

        assertThat(versioned.retrieveWithProvenance(question, List.of(evidence), 1)
                .matches().getFirst().provenance().retrievalVersion())
                .isEqualTo("custom-retriever-v7");
        assertThat(versioned.retrieveBatchWithProvenance(List.of(question), List.of(evidence), 1)
                .get("1").matches().getFirst().provenance().retrievalVersion())
                .isEqualTo("custom-retriever-v7");
    }

    private EvidenceChunk chunk(String content) {
        return new EvidenceChunk(UUID.randomUUID(), "beleg.pdf", "application/pdf", "Seite 1", content, null);
    }
}
