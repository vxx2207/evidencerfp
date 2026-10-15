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
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Component
@Profile("!ollama & !openai")
public class DeterministicAnswerEngine implements AnswerGenerator {
    private final CitationVerifier citationVerifier;
    private final EvidenceAnswerPolicy evidenceAnswerPolicy;

    public DeterministicAnswerEngine(CitationVerifier citationVerifier, EvidenceAnswerPolicy evidenceAnswerPolicy) {
        this.citationVerifier = citationVerifier;
        this.evidenceAnswerPolicy = evidenceAnswerPolicy;
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
        List<String> sentences = Arrays.stream(content.split("(?<=[.!?])\\s+|\\n+"))
                .map(String::trim).filter(sentence -> !sentence.isBlank()).toList();
        String selected = sentences.stream()
                .max(Comparator.comparingInt(sentence -> EvidenceTextTerms.overlap(
                        questionTerms, EvidenceTextTerms.tokens(sentence))))
                .orElse(content.trim());
        return selected.length() <= 360 ? selected : selected.substring(0, 360).trim();
    }

}
