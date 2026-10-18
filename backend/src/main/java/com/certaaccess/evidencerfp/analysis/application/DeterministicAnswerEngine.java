package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.Citation;
import com.certaaccess.evidencerfp.analysis.domain.Finding;
import com.certaaccess.evidencerfp.analysis.domain.FindingSeverity;
import com.certaaccess.evidencerfp.analysis.domain.FindingType;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import com.certaaccess.evidencerfp.evidence.CitationVerifier;
import com.certaaccess.evidencerfp.evidence.EvidenceChunk;
import com.certaaccess.evidencerfp.evidence.EvidenceTextTerms;
import com.certaaccess.evidencerfp.evidence.RfpQuestion;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@Profile("!ollama & !openai")
public class DeterministicAnswerEngine implements AnswerGenerator {
    private static final Set<String> GENERIC_ONLY_TERMS = Set.of(
            "aufbewahrung", "daten", "dokumentiert", "gespeichert", "gilt", "kryptoschutz",
            "lifecycleaktion", "löschung", "speicherung", "speichert", "speicherte",
            "verarbeitet", "behandelt", "wert");
    private static final Pattern CONFLICT_CUE = Pattern.compile(
            "(?i)\\b(?:widerspr\\p{L}*|historisch\\p{L}*|ersetzt|älter\\p{L}*|"
                    + "aktualitätshinweis|neuere|gilt\\s+nur)\\b");
    private static final Pattern FACT_VALUE = Pattern.compile(
            "(?i)\\b(\\d{2,4})\\s*(tage|minuten|stunden|prozent|%)\\b");
    private final CitationVerifier citationVerifier;
    private final EvidenceAnswerPolicy evidenceAnswerPolicy;

    public DeterministicAnswerEngine(CitationVerifier citationVerifier, EvidenceAnswerPolicy evidenceAnswerPolicy) {
        this.citationVerifier = citationVerifier;
        this.evidenceAnswerPolicy = evidenceAnswerPolicy;
    }

    @Override
    public QuestionResult generate(RfpQuestion question, List<EvidenceChunk> retrievedChunks) {
        Set<String> questionTerms = EvidenceTextTerms.tokens(question.question());
        Set<String> selectionTerms = questionTerms;
        if (retrievedChunks.isEmpty()) return notFound(question);

        List<ScoredChunk> scored = java.util.stream.IntStream.range(0, retrievedChunks.size())
                .mapToObj(index -> new ScoredChunk(retrievedChunks.get(index),
                        EvidenceTextTerms.relevance(selectionTerms, retrievedChunks.get(index)), index))
                .filter(candidate -> hasDiscriminativeSupport(questionTerms, candidate.chunk()))
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed()
                        .thenComparingInt(ScoredChunk::retrievalRank))
                .toList();
        if (scored.isEmpty()) return notFound(question);
        EvidenceChunk primary = scored.getFirst().chunk();
        Set<String> primaryEvidenceTerms = new HashSet<>(EvidenceTextTerms.tokens(primary.content()));
        primaryEvidenceTerms.addAll(EvidenceTextTerms.tokens(primary.locator()));
        int bestScore = EvidenceTextTerms.overlap(questionTerms, primaryEvidenceTerms);
        // Kurze Normbegriffe wie TLS sind allein aussagekräftig; längere Fragen benötigen
        // dagegen eine belastbare Konzeptabdeckung. Das hält fachfremde Treffer fail-closed.
        int requiredScore = 1;
        if (bestScore < requiredScore) return notFound(question);
        Set<String> bestTerms = new HashSet<>(questionTerms);
        bestTerms.retainAll(primaryEvidenceTerms);
        if (GENERIC_ONLY_TERMS.containsAll(bestTerms)) return notFound(question);

        List<EvidenceChunk> citedChunks = new ArrayList<>();
        citedChunks.add(primary);
        int minimumConflictScore = Math.max(1, (int) Math.ceil(scored.getFirst().score() * 0.65));
        scored.stream()
                .filter(candidate -> candidate.score() >= minimumConflictScore
                        || explicitConflictCues(primary, candidate.chunk()))
                .map(ScoredChunk::chunk)
                .filter(candidate -> !candidate.id().equals(primary.id()))
                .filter(candidate -> potentialConflict(primary, candidate))
                .findFirst()
                .ifPresent(citedChunks::add);
        List<Citation> citations = citedChunks.stream()
                .map(chunk -> new Citation(chunk.id(), chunk.documentName(), chunk.locator(), quote(chunk.content(), questionTerms)))
                .toList();
        if (!citationVerifier.verify(citations, retrievedChunks)) {
            Finding invalid = Finding.of(FindingType.MISSING_EVIDENCE, FindingSeverity.HIGH,
                    "EVIDENCE-004", "Zitationsprüfung fehlgeschlagen",
                    "Mindestens eine erzeugte Zitation ließ sich nicht exakt auf einen abgerufenen Chunk zurückführen.",
                    question.id(), "Antwort verwerfen und Retrieval erneut ausführen.", true);
            return new QuestionResult(question.id(), question.question(), question.category(),
                    "Antwort verworfen: Die Quellenangabe konnte nicht verifiziert werden.", AnswerStatus.NOT_FOUND,
                    ReviewStatus.DRAFT, List.of(),
                    "Welche verifizierbare Textpassage beantwortet diese Frage?", List.of(invalid));
        }

        String answer = citations.size() == 1
                ? "Die bereitgestellte Quelle dokumentiert: " + citations.getFirst().quote()
                : "Die bereitgestellten Quellen dokumentieren: " + citations.getFirst().quote()
                    + " Ergänzend: " + citations.get(1).quote();
        QuestionResult draft = new QuestionResult(question.id(), question.question(), question.category(), answer,
                AnswerStatus.SUPPORTED, ReviewStatus.DRAFT, citations, null, List.of());
        return evidenceAnswerPolicy.enforce(question, draft, retrievedChunks);
    }

    private QuestionResult notFound(RfpQuestion question) {
        Finding missing = Finding.of(FindingType.MISSING_EVIDENCE, FindingSeverity.WARNING,
                "EVIDENCE-001", "Kein belastbarer Beleg",
                "Keine der bereitgestellten Textpassagen deckt die Frage ausreichend ab.", question.id(),
                "Eine belastbare Aussage bei Product Management, Security oder Legal anfragen.", true);
        return new QuestionResult(question.id(), question.question(), question.category(),
                "Keine belastbare Aussage in den bereitgestellten Quellen gefunden.", AnswerStatus.NOT_FOUND,
                ReviewStatus.DRAFT, List.of(),
                "Welche freigegebene Quelle kann diese Anforderung verbindlich beantworten?", List.of(missing));
    }

    private String quote(String content, Set<String> questionTerms) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.length() <= 1_200) return normalized;
        List<String> sentences = Arrays.stream(content.split("(?<=[.!?])\\s+|\\n+"))
                .map(String::trim).filter(sentence -> !sentence.isBlank()).toList();
        String selected = sentences.stream()
                .max(Comparator.comparingInt(sentence -> EvidenceTextTerms.overlap(
                        questionTerms, EvidenceTextTerms.tokens(sentence))))
                .orElse(content.trim());
        return selected.length() <= 1_200 ? selected : selected.substring(0, 1_200).trim();
    }

    private boolean potentialConflict(EvidenceChunk first, EvidenceChunk second) {
        if (first.documentName().equals(second.documentName())) return false;
        if (explicitConflictCues(first, second)) return true;
        java.util.Map<String, Set<String>> firstFacts = facts(first.content());
        java.util.Map<String, Set<String>> secondFacts = facts(second.content());
        return firstFacts.entrySet().stream().anyMatch(entry -> {
            Set<String> otherValues = secondFacts.get(entry.getKey());
            if (otherValues == null) return false;
            Set<String> combined = new HashSet<>(entry.getValue());
            combined.addAll(otherValues);
            return combined.size() > 1;
        });
    }

    private java.util.Map<String, Set<String>> facts(String content) {
        java.util.Map<String, Set<String>> result = new java.util.HashMap<>();
        var matcher = FACT_VALUE.matcher(content);
        while (matcher.find()) {
            String unit = "%".equals(matcher.group(2))
                    ? "prozent" : matcher.group(2).toLowerCase(java.util.Locale.GERMAN);
            result.computeIfAbsent(unit, ignored -> new HashSet<>()).add(matcher.group(1));
        }
        return result;
    }

    private boolean hasDiscriminativeSupport(Set<String> questionTerms, EvidenceChunk chunk) {
        Set<String> evidenceTerms = new HashSet<>(EvidenceTextTerms.tokens(chunk.content()));
        evidenceTerms.addAll(EvidenceTextTerms.tokens(chunk.locator()));
        Set<String> overlap = new HashSet<>(questionTerms);
        overlap.retainAll(evidenceTerms);
        return !overlap.isEmpty() && !GENERIC_ONLY_TERMS.containsAll(overlap);
    }

    private boolean explicitConflictCues(EvidenceChunk first, EvidenceChunk second) {
        return !first.documentName().equals(second.documentName())
                && CONFLICT_CUE.matcher(first.content()).find()
                && CONFLICT_CUE.matcher(second.content()).find();
    }

    private record ScoredChunk(EvidenceChunk chunk, int score, int retrievalRank) { }

}
