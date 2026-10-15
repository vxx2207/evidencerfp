package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.FindingType;
import com.certaaccess.evidencerfp.evidence.CitationVerifier;
import com.certaaccess.evidencerfp.evidence.EvidenceChunk;
import com.certaaccess.evidencerfp.evidence.RfpQuestion;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicAnswerEngineTest {
    private final DeterministicAnswerEngine engine = new DeterministicAnswerEngine(
            new CitationVerifier(), new EvidenceAnswerPolicy(
                    Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC), 365));

    @Test
    void flagsDifferentRetentionValuesAsConflict() {
        RfpQuestion question = new RfpQuestion("RFP-016",
                "Wie lange werden Audit-Ereignisse standardmäßig aufbewahrt?", "Audit");
        List<EvidenceChunk> chunks = List.of(
                chunk("platform.pdf", "Audit-Ereignisse wurden standardmäßig 365 Tage aufbewahrt."),
                chunk("operations.pdf", "Audit-Ereignisse werden 400 Tage aufbewahrt; der Wert ersetzt den älteren Standard."));

        var result = engine.generate(question, chunks);

        assertThat(result.answerStatus()).isEqualTo(AnswerStatus.PARTIAL);
        assertThat(result.findings()).extracting(finding -> finding.type())
                .contains(FindingType.CONFLICTING_EVIDENCE);
    }

    @Test
    void doesNotTurnAWeakKeywordMatchIntoAnAnswer() {
        RfpQuestion question = new RfpQuestion("RFP-020",
                "Welche quantenresistenten Algorithmen sind bereits produktiv aktiviert?", "Kryptografie");

        var result = engine.generate(question,
                List.of(chunk("authentication.pdf", "Für Administratoren ist FIDO2 standardmäßig aktiviert.")));

        assertThat(result.answerStatus()).isEqualTo(AnswerStatus.NOT_FOUND);
        assertThat(result.citations()).isEmpty();
    }

    private EvidenceChunk chunk(String document, String content) {
        return new EvidenceChunk(UUID.randomUUID(), document, "application/pdf", "Seite 1", content, null);
    }
}
