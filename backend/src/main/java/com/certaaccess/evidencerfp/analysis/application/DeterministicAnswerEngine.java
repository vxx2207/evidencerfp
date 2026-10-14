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

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@Profile("!ollama & !openai")
public class DeterministicAnswerEngine implements AnswerGenerator {
    private static final Pattern NEGATIVE = Pattern.compile(
            "(?i)\\b(?:nicht unterstützt|nicht verfügbar|nicht als|deaktiviert|verhindert|keine?\\s+Unterstützung|ausgeschlossen)\\b");
    private static final Pattern POSITIVE = Pattern.compile(
            "(?i)\\b(?:unterstützt|verfügbar|aktiviert|ermöglicht|bietet|konnten|zulassen|verwenden)\\b");
    private static final Pattern CONFLICT_HINT = Pattern.compile(
            "(?i)\\b(?:widerspr\\p{L}*|historisch\\p{L}*|ersetzt\\s+den\\s+älter\\p{L}*)\\b");
    private static final Pattern FACT_VALUE = Pattern.compile(
            "(?i)\\b(\\d{2,4})\\s*(tage|minuten|stunden|prozent|%)\\b");

    private final CitationVerifier citationVerifier;
    private final Clock clock;

    public DeterministicAnswerEngine(CitationVerifier citationVerifier, Clock clock) {
        this.citationVerifier = citationVerifier;
        this.clock = clock;
    }

    @Override
    public QuestionResult generate(RfpQuestion question, List<EvidenceChunk> retrievedChunks) {
        Set<String> questionTerms = EvidenceTextTerms.tokens(question.question());
        if (retrievedChunks.isEmpty()) return notFound(question);

        int bestScore = EvidenceTextTerms.overlap(questionTerms,
                EvidenceTextTerms.tokens(retrievedChunks.getFirst().content()));
        int requiredScore = Math.max(2, (int) Math.ceil(questionTerms.size() * 0.45));
        if (bestScore < requiredScore) return notFound(question);

        List<EvidenceChunk> citedChunks = retrievedChunks.stream()
                .filter(chunk -> EvidenceTextTerms.overlap(questionTerms,
                        EvidenceTextTerms.tokens(chunk.content())) >= Math.max(requiredScore, bestScore - 1))
                .limit(2)
                .toList();
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

        List<Finding> findings = new java.util.ArrayList<>();
        boolean conflict = hasConflict(citedChunks, questionTerms);
        if (conflict) {
            findings.add(Finding.of(FindingType.CONFLICTING_EVIDENCE, FindingSeverity.HIGH,
                    "EVIDENCE-002", "Widersprüchliche Quellen",
                    "Die am besten passenden Quellen enthalten positive und negative Aussagen zur Anforderung.",
                    question.id(), "Die gültige Aussage durch den fachlichen Owner bestätigen lassen.", true));
        }
        LocalDate staleBefore = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(365);
        boolean stale = citedChunks.stream().anyMatch(chunk -> chunk.documentDate() != null
                && chunk.documentDate().isBefore(staleBefore));
        if (stale) {
            findings.add(Finding.of(FindingType.STALE_DOCUMENT, FindingSeverity.WARNING,
                    "EVIDENCE-003", "Veralteter Nachweis",
                    "Mindestens eine zitierte Quelle ist älter als zwölf Monate.", question.id(),
                    "Eine aktuelle, freigegebene Dokumentversion anfordern.", true));
        }

        AnswerStatus status = !conflict && !stale ? AnswerStatus.SUPPORTED : AnswerStatus.PARTIAL;
        String answer = citations.size() == 1
                ? "Die bereitgestellte Quelle dokumentiert: " + citations.getFirst().quote()
                : "Die bereitgestellten Quellen dokumentieren: " + citations.getFirst().quote()
                    + " Ergänzend: " + citations.get(1).quote();
        String clarification = status == AnswerStatus.PARTIAL
                ? "Kann der fachliche Owner die Aussage und Aktualität der Belege verbindlich bestätigen?" : null;
        return new QuestionResult(question.id(), question.question(), question.category(), answer, status,
                ReviewStatus.DRAFT, citations, clarification, findings);
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
        List<String> sentences = Arrays.stream(content.split("(?<=[.!?])\\s+|\\n+"))
                .map(String::trim).filter(sentence -> !sentence.isBlank()).toList();
        String selected = sentences.stream()
                .max(Comparator.comparingInt(sentence -> EvidenceTextTerms.overlap(
                        questionTerms, EvidenceTextTerms.tokens(sentence))))
                .orElse(content.trim());
        return selected.length() <= 360 ? selected : selected.substring(0, 360).trim();
    }

    private boolean hasConflict(List<EvidenceChunk> chunks, Set<String> questionTerms) {
        if (chunks.size() < 2 || chunks.get(0).documentName().equals(chunks.get(1).documentName())) return false;
        List<String> relevantTexts = chunks.stream().map(chunk -> quote(chunk.content(), questionTerms)).toList();
        if (relevantTexts.stream().anyMatch(text -> CONFLICT_HINT.matcher(text).find())) return true;
        Set<String> values = new HashSet<>();
        relevantTexts.forEach(text -> {
            var matcher = FACT_VALUE.matcher(text);
            while (matcher.find()) values.add(matcher.group(1) + matcher.group(2).toLowerCase(Locale.GERMAN));
        });
        if (values.size() > 1) return true;
        boolean negative = relevantTexts.stream().anyMatch(text -> NEGATIVE.matcher(text).find());
        boolean positive = relevantTexts.stream().anyMatch(text -> !NEGATIVE.matcher(text).find()
                && POSITIVE.matcher(text).find());
        return negative && positive;
    }
}
