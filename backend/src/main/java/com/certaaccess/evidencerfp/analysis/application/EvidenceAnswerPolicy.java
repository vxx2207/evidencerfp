package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.Citation;
import com.certaaccess.evidencerfp.analysis.domain.Finding;
import com.certaaccess.evidencerfp.analysis.domain.FindingSeverity;
import com.certaaccess.evidencerfp.analysis.domain.FindingType;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.evidence.EvidenceChunk;
import com.certaaccess.evidencerfp.evidence.RfpQuestion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Erzwingt Evidenzregeln nach der Antworterzeugung unabhängig vom verwendeten Modell.
 * Nur tatsächlich zitierte, zuvor abgerufene Chunks fließen in die Bewertung ein.
 */
@Component
public class EvidenceAnswerPolicy {
    private static final Pattern NEGATIVE = Pattern.compile(
            "(?i)\\b(?:nicht unterstützt|nicht verfügbar|nicht als|deaktiviert|verhindert|keine?\\s+Unterstützung|ausgeschlossen)\\b");
    private static final Pattern POSITIVE = Pattern.compile(
            "(?i)\\b(?:unterstützt|verfügbar|aktiviert|ermöglicht|bietet|konnten|zulassen|verwenden)\\b");
    private static final Pattern CONFLICT_HINT = Pattern.compile(
            "(?i)\\b(?:widerspr\\p{L}*|historisch\\p{L}*|ersetzt\\s+den\\s+älter\\p{L}*)\\b");
    private static final Pattern FACT_VALUE = Pattern.compile(
            "(?i)\\b(\\d{2,4})\\s*(tage|minuten|stunden|prozent|%)\\b");

    private final Clock clock;
    private final int staleAfterDays;

    public EvidenceAnswerPolicy(Clock clock,
                                @Value("${evidence-rfp.documents.stale-after-days:365}") int staleAfterDays) {
        this.clock = clock;
        this.staleAfterDays = staleAfterDays;
    }

    public QuestionResult enforce(RfpQuestion question, QuestionResult result,
                                  List<EvidenceChunk> retrievedChunks) {
        if (result.answerStatus() == AnswerStatus.NOT_FOUND || result.citations().isEmpty()) {
            return result;
        }

        Map<UUID, EvidenceChunk> retrievedById = new HashMap<>();
        retrievedChunks.forEach(chunk -> retrievedById.put(chunk.id(), chunk));
        Map<UUID, EvidenceChunk> citedById = new LinkedHashMap<>();
        for (Citation citation : result.citations()) {
            EvidenceChunk chunk = retrievedById.get(citation.chunkId());
            if (chunk != null) citedById.putIfAbsent(chunk.id(), chunk);
        }
        List<EvidenceChunk> citedChunks = List.copyOf(citedById.values());
        if (citedChunks.isEmpty()) return result;

        List<Finding> findings = new ArrayList<>(result.findings());
        boolean conflict = hasConflict(result.citations(), citedChunks);
        boolean stale = isStale(citedChunks);
        if (conflict && findings.stream().noneMatch(finding -> "EVIDENCE-002".equals(finding.ruleId()))) {
            findings.add(Finding.of(FindingType.CONFLICTING_EVIDENCE, FindingSeverity.HIGH,
                    "EVIDENCE-002", "Widersprüchliche Quellen",
                    "Die zitierten Quellen enthalten widersprüchliche Aussagen zur Anforderung.",
                    question.id(), "Die gültige Aussage durch den fachlichen Owner bestätigen lassen.", true));
        }
        if (stale && findings.stream().noneMatch(finding -> "EVIDENCE-003".equals(finding.ruleId()))) {
            findings.add(Finding.of(FindingType.STALE_DOCUMENT, FindingSeverity.WARNING,
                    "EVIDENCE-003", "Veralteter Nachweis",
                    "Mindestens eine zitierte Quelle ist älter als den konfigurierten Aktualitätszeitraum.",
                    question.id(), "Eine aktuelle, freigegebene Dokumentversion anfordern.", true));
        }
        if (!conflict && !stale) return result;

        String clarification = result.internalClarification();
        if (clarification == null || clarification.isBlank()) {
            clarification = conflict && stale
                    ? "Kann der fachliche Owner die widersprüchliche Aussage mit einer aktuellen Quelle bestätigen?"
                    : conflict
                    ? "Kann der fachliche Owner die gültige Aussage verbindlich bestätigen?"
                    : "Kann der fachliche Owner einen aktuellen, freigegebenen Beleg bereitstellen?";
        }
        return new QuestionResult(result.id(), result.question(), result.category(), result.answer(),
                AnswerStatus.PARTIAL, result.reviewStatus(), result.citations(), clarification, findings);
    }

    private boolean isStale(List<EvidenceChunk> chunks) {
        LocalDate staleBefore = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(staleAfterDays);
        return chunks.stream().anyMatch(chunk -> chunk.documentDate() != null
                && chunk.documentDate().isBefore(staleBefore));
    }

    private boolean hasConflict(List<Citation> citations, List<EvidenceChunk> citedChunks) {
        Map<UUID, EvidenceChunk> chunksById = new HashMap<>();
        citedChunks.forEach(chunk -> chunksById.put(chunk.id(), chunk));
        List<CitedText> texts = citations.stream()
                .filter(citation -> chunksById.containsKey(citation.chunkId()))
                .map(citation -> new CitedText(citation.chunkId(), citation.quote()))
                .filter(cited -> cited.text() != null && !cited.text().isBlank())
                .toList();
        if (texts.stream().map(CitedText::sourceId).distinct().count() < 2) return false;
        if (texts.stream().anyMatch(cited -> CONFLICT_HINT.matcher(cited.text()).find())) return true;
        if (hasDifferentFactsAcrossDocuments(texts)) return true;
        boolean negative = texts.stream().anyMatch(cited -> NEGATIVE.matcher(cited.text()).find());
        boolean positive = texts.stream().anyMatch(cited -> !NEGATIVE.matcher(cited.text()).find()
                && POSITIVE.matcher(cited.text()).find());
        return negative && positive;
    }

    private boolean hasDifferentFactsAcrossDocuments(List<CitedText> texts) {
        Map<String, Map<UUID, Set<String>>> valuesByUnitAndSource = new HashMap<>();
        for (CitedText cited : texts) {
            var matcher = FACT_VALUE.matcher(cited.text());
            while (matcher.find()) {
                String unit = normalizedUnit(matcher.group(2));
                valuesByUnitAndSource.computeIfAbsent(unit, ignored -> new HashMap<>())
                        .computeIfAbsent(cited.sourceId(), ignored -> new HashSet<>())
                        .add(matcher.group(1));
            }
        }
        return valuesByUnitAndSource.values().stream().anyMatch(bySource ->
                bySource.size() > 1 && bySource.values().stream()
                        .flatMap(Set::stream).distinct().count() > 1);
    }

    private String normalizedUnit(String value) {
        String unit = value.toLowerCase(Locale.GERMAN);
        return "%".equals(unit) ? "prozent" : unit;
    }

    private record CitedText(UUID sourceId, String text) { }
}
