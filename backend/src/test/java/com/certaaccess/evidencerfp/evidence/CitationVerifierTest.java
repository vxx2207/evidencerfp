package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.analysis.domain.Citation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CitationVerifierTest {
    private final CitationVerifier verifier = new CitationVerifier();

    @Test
    void acceptsExactQuoteFromRetrievedChunkDespiteWhitespaceDifferences() {
        UUID id = UUID.randomUUID();
        EvidenceChunk chunk = new EvidenceChunk(id, "produkt.pdf", "application/pdf", "Seite 2",
                "CertaAccess unterstützt   FIDO2 und Passkeys für privilegierte Konten.",
                LocalDate.of(2026, 9, 30));
        Citation citation = new Citation(id, "produkt.pdf", "Seite 2",
                "unterstützt FIDO2 und Passkeys");

        assertThat(verifier.verify(List.of(citation), List.of(chunk))).isTrue();
    }

    @Test
    void rejectsHallucinatedQuote() {
        UUID id = UUID.randomUUID();
        EvidenceChunk chunk = new EvidenceChunk(id, "produkt.pdf", "application/pdf", "Seite 2",
                "FIDO2 wird unterstützt.", null);

        assertThat(verifier.verify(
                List.of(new Citation(id, "produkt.pdf", "Seite 2", "SOC 2 Typ II ist zertifiziert.")),
                List.of(chunk))).isFalse();
    }

    @Test
    void rejectsChunkThatWasNotRetrieved() {
        EvidenceChunk allowed = new EvidenceChunk(UUID.randomUUID(), "a.pdf", "application/pdf", "Seite 1",
                "Audit-Ereignisse können exportiert werden.", null);
        Citation citation = new Citation(UUID.randomUUID(), "b.pdf", "Seite 7",
                "Audit-Ereignisse können exportiert werden.");

        assertThat(verifier.verify(List.of(citation), List.of(allowed))).isFalse();
    }

    @Test
    void acceptsClaimWhenNumberEntityAndPolarityAreSupported() {
        UUID id = UUID.randomUUID();
        EvidenceChunk chunk = new EvidenceChunk(id, "mfa.pdf", "application/pdf", "Seite 4",
                "Ab Version 4.2 verhindert die Plattform eine Sitzung, wenn ausschließlich TOTP verfügbar ist.",
                null);
        Citation citation = new Citation(id, "mfa.pdf", "Seite 4",
                "Version 4.2 verhindert die Plattform eine Sitzung, wenn ausschließlich TOTP verfügbar ist");

        assertThat(verifier.verifyClaim(
                "Seit Version 4.2 wird eine nur mit TOTP geschützte Sitzung verhindert.",
                List.of(citation), List.of(chunk))).isTrue();
    }

    @Test
    void rejectsClaimThatAddsUnsupportedNumber() {
        UUID id = UUID.randomUUID();
        EvidenceChunk chunk = new EvidenceChunk(id, "pam.pdf", "application/pdf", "Seite 2",
                "Eine genehmigte Aktivierung gilt standardmäßig 60 Minuten.", null);
        Citation citation = new Citation(id, "pam.pdf", "Seite 2",
                "Eine genehmigte Aktivierung gilt standardmäßig 60 Minuten");

        assertThat(verifier.verifyClaim("Eine Aktivierung gilt 120 Minuten.",
                List.of(citation), List.of(chunk))).isFalse();
    }

    @Test
    void rejectsClaimThatInvertsNegation() {
        UUID id = UUID.randomUUID();
        EvidenceChunk chunk = new EvidenceChunk(id, "auth.pdf", "application/pdf", "Seite 1",
                "SMS gilt nicht als phishing-resistent.", null);
        Citation citation = new Citation(id, "auth.pdf", "Seite 1",
                "SMS gilt nicht als phishing-resistent");

        assertThat(verifier.verifyClaim("SMS gilt als phishing-resistent.",
                List.of(citation), List.of(chunk))).isFalse();
    }

    @Test
    void rejectsClaimThatAddsUnsupportedCamelCaseEntity() {
        UUID id = UUID.randomUUID();
        EvidenceChunk chunk = new EvidenceChunk(id, "auth.pdf", "application/pdf", "Seite 2",
                "Die Plattform unterstützt FIDO2 für privilegierte Konten.", null);
        Citation citation = new Citation(id, "auth.pdf", "Seite 2",
                "Die Plattform unterstützt FIDO2 für privilegierte Konten");

        assertThat(verifier.verifyClaim(
                "CertaAccess unterstützt FIDO2 für privilegierte Konten.",
                List.of(citation), List.of(chunk))).isFalse();
    }
}
