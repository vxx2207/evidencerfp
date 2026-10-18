package com.certaaccess.evidencerfp.evidence;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceChunkerTest {
    private final EvidenceChunker chunker = new EvidenceChunker();

    @Test
    void createsStableIdsAndPreservesSectionInLocator() {
        String text = "## Verschlüsselung\nPersistente Inhalte verwenden AES-256.\n\n"
                + "## Transport\nBrowser verwenden TLS 1.3.";

        var first = chunker.chunk("a".repeat(64), "security.docx", "application/docx",
                "Dokument", text, LocalDate.of(2026, 1, 1));
        var second = chunker.chunk("a".repeat(64), "security.docx", "application/docx",
                "Dokument", text, LocalDate.of(2026, 1, 1));

        assertThat(first).extracting(EvidenceChunk::id)
                .containsExactlyElementsOf(second.stream().map(EvidenceChunk::id).toList());
        assertThat(first).extracting(EvidenceChunk::locator)
                .containsExactly("Dokument — Verschlüsselung", "Dokument — Transport");
    }

    @Test
    void limitsChunksToFourHundredFiftyTokensWithBoundedOverlap() {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 1_000; index++) {
            text.append("wort").append(index).append(index % 25 == 24 ? ". " : " ");
        }

        var chunks = chunker.chunk("b".repeat(64), "large.pdf", "application/pdf",
                "Seite 1", text.toString(), null);

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allMatch(chunk -> chunk.content().split("\\s+").length <= 450);
        assertThat(chunks.get(1).content().split("\\s+")[0]).isEqualTo("wort400");
        assertThat(chunks).extracting(EvidenceChunk::id).doesNotHaveDuplicates();
    }
}
