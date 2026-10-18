package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.analysis.application.DeterministicAnswerEngine;
import com.certaaccess.evidencerfp.analysis.application.EvidenceAnswerPolicy;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.Citation;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Reproduzierbare Offline-Evaluation; schreibt JSON und HTML nach target/evaluation. */
class GoldenSetEvaluationTest {
    private static final Pattern DOCUMENT_ID = Pattern.compile("(?m)^documentId:\\s*(\\S+)\\s*$");
    private static final Pattern SECTION = Pattern.compile("(?m)^##\\s+(.+?)\\s+\\{#([^}]+)}\\s*$");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void deterministicBaselineMeetsVersionedQualityGatesAndWritesReports() throws IOException {
        Path examples = Path.of("..", "examples");
        GoldenSet goldenSet = JSON.readValue(examples.resolve("golden-set.json").toFile(), GoldenSet.class);
        List<EvaluationCase> cases = expand(goldenSet.questions());
        assertThat(cases).hasSizeGreaterThanOrEqualTo(80);

        List<EvidenceChunk> chunks = markdownChunks(examples.resolve("source"));
        EvaluationReport report = evaluate(goldenSet, cases, chunks, new LexicalEvidenceRetriever());
        writeReports(report, Path.of("target", "evaluation"));

        assertThat(report.metrics().recallAtSix()).isGreaterThanOrEqualTo(0.95);
        assertThat(report.metrics().meanReciprocalRank()).isGreaterThanOrEqualTo(0.85);
        assertThat(report.metrics().statusMacroF1()).isGreaterThanOrEqualTo(0.90);
        assertThat(report.metrics().requiredTermCoverage()).isGreaterThanOrEqualTo(0.95);
        assertThat(report.metrics().citationPrecision()).isEqualTo(1.0);
        assertThat(report.metrics().notFoundPrecision()).isEqualTo(1.0);
        assertThat(report.metrics().notFoundRecall()).isEqualTo(1.0);
    }

    private EvaluationReport evaluate(GoldenSet goldenSet, List<EvaluationCase> cases,
                                      List<EvidenceChunk> chunks, EvidenceRetriever retriever) {
        CitationVerifier citationVerifier = new CitationVerifier();
        DeterministicAnswerEngine answerEngine = new DeterministicAnswerEngine(
                citationVerifier, new EvidenceAnswerPolicy(
                Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC), 365));
        List<CaseResult> results = new ArrayList<>();
        int answerable = 0;
        int retrievalHits = 0;
        double reciprocalRankSum = 0;
        int requiredTerms = 0;
        int coveredTerms = 0;
        int citations = 0;
        int validCitations = 0;
        Map<AnswerStatus, Confusion> confusion = new EnumMap<>(AnswerStatus.class);
        for (AnswerStatus status : AnswerStatus.values()) confusion.put(status, new Confusion());

        for (EvaluationCase evaluationCase : cases) {
            RfpQuestion question = new RfpQuestion(evaluationCase.id(), evaluationCase.question(),
                    evaluationCase.category());
            List<EvidenceChunk> retrieved = retriever.retrieve(question, chunks, 6);
            int firstRelevantRank = firstRelevantRank(retrieved, evaluationCase.expectedSources());
            if (evaluationCase.expectedStatus() != AnswerStatus.NOT_FOUND) {
                answerable++;
                if (firstRelevantRank > 0) {
                    retrievalHits++;
                    reciprocalRankSum += 1.0 / firstRelevantRank;
                }
            }

            QuestionResult answer = answerEngine.generate(question, retrieved);
            for (AnswerStatus status : AnswerStatus.values()) {
                Confusion values = confusion.get(status);
                if (answer.answerStatus() == status && evaluationCase.expectedStatus() == status) values.tp++;
                else if (answer.answerStatus() == status) values.fp++;
                else if (evaluationCase.expectedStatus() == status) values.fn++;
            }
            String supportedText = normalize(answer.answer() + " " + answer.citations().stream()
                    .map(Citation::quote).reduce("", (left, right) -> left + " " + right));
            List<String> missingTerms = new ArrayList<>();
            for (String required : evaluationCase.requiredTerms()) {
                requiredTerms++;
                if (supportedText.contains(normalize(required))) coveredTerms++;
                else missingTerms.add(required);
            }
            for (Citation citation : answer.citations()) {
                citations++;
                if (citationVerifier.verify(List.of(citation), retrieved)) validCitations++;
            }
            results.add(new CaseResult(evaluationCase.id(), evaluationCase.question(),
                    evaluationCase.expectedStatus(), answer.answerStatus(), firstRelevantRank,
                    answer.citations().size(), missingTerms,
                    answer.citations().stream()
                            .map(citation -> citation.documentName() + "/" + citation.locator())
                            .toList(),
                    answer.citations().stream().map(Citation::quote).toList(),
                    retrieved.stream()
                            .map(chunk -> chunk.documentName() + "/" + chunk.locator())
                            .toList()));
        }

        double macroF1 = confusion.values().stream().mapToDouble(Confusion::f1).average().orElse(0);
        Confusion notFound = confusion.get(AnswerStatus.NOT_FOUND);
        EvaluationMetrics metrics = new EvaluationMetrics(
                ratio(retrievalHits, answerable), answerable == 0 ? 0 : reciprocalRankSum / answerable,
                macroF1, ratio(coveredTerms, requiredTerms), ratio(validCitations, citations),
                notFound.precision(), notFound.recall(), cases.size());
        Map<String, String> versions = new LinkedHashMap<>(goldenSet.versions());
        versions.put("dataset", goldenSet.datasetId());
        versions.put("evaluatedRetriever", "lexical-deterministic-v2");
        return new EvaluationReport(Instant.parse("2026-10-14T00:00:00Z").toString(),
                goldenSet.datasetId(), versions, metrics, results);
    }

    private List<EvaluationCase> expand(List<GoldenQuestion> questions) {
        List<EvaluationCase> result = new ArrayList<>();
        for (GoldenQuestion question : questions) {
            List<String> variants = new ArrayList<>();
            variants.add(question.question());
            variants.addAll(question.paraphrases() == null ? List.of() : question.paraphrases());
            for (int index = 0; index < variants.size(); index++) {
                result.add(new EvaluationCase(question.id() + "-" + (index + 1), question.category(),
                        variants.get(index), AnswerStatus.valueOf(question.expectedStatus()),
                        question.expectedSources(), question.requiredTerms()));
            }
        }
        return result;
    }

    private int firstRelevantRank(List<EvidenceChunk> chunks, List<ExpectedSource> expected) {
        for (int index = 0; index < chunks.size(); index++) {
            EvidenceChunk chunk = chunks.get(index);
            if (expected.stream().anyMatch(source -> source.documentId().equals(chunk.documentName())
                    && source.sectionId().equals(chunk.locator()))) return index + 1;
        }
        return 0;
    }

    private List<EvidenceChunk> markdownChunks(Path sourceDirectory) throws IOException {
        List<EvidenceChunk> chunks = new ArrayList<>();
        try (var files = Files.list(sourceDirectory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().matches("\\d{2}-.+\\.md"))
                    .sorted().toList()) {
                String markdown = Files.readString(file);
                Matcher documentMatcher = DOCUMENT_ID.matcher(markdown);
                assertThat(documentMatcher.find()).as("documentId in %s", file).isTrue();
                String documentId = documentMatcher.group(1);
                Matcher sectionMatcher = SECTION.matcher(markdown);
                List<SectionStart> starts = new ArrayList<>();
                while (sectionMatcher.find()) {
                    starts.add(new SectionStart(sectionMatcher.group(2), sectionMatcher.start(), sectionMatcher.end()));
                }
                for (int index = 0; index < starts.size(); index++) {
                    SectionStart current = starts.get(index);
                    int end = index + 1 < starts.size() ? starts.get(index + 1).start() : markdown.length();
                    String content = markdown.substring(current.contentStart(), end).trim();
                    String hash = EvidenceChunkIdentity.sha256(markdown);
                    chunks.add(new EvidenceChunk(EvidenceChunkIdentity.stableId(hash, current.id(), content),
                            documentId, "text/markdown", current.id(), content, null));
                }
            }
        }
        return chunks;
    }

    private void writeReports(EvaluationReport report, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("golden-set-report.json"),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        String rows = report.results().stream().map(result -> "<tr><td>" + html(result.id())
                        + "</td><td>" + html(result.expectedStatus().name()) + "</td><td>"
                        + html(result.actualStatus().name()) + "</td><td>" + result.firstRelevantRank()
                        + "</td><td>" + result.citationCount() + "</td><td>"
                        + html(String.join(", ", result.missingTerms())) + "</td><td>"
                        + html(String.join(", ", result.citedSources())) + "</td></tr>")
                .reduce("", String::concat);
        EvaluationMetrics metrics = report.metrics();
        String versions = report.versions().entrySet().stream()
                .map(entry -> html(entry.getKey()) + "=<code>" + html(entry.getValue()) + "</code>")
                .reduce((left, right) -> left + " · " + right).orElse("");
        String html = """
                <!doctype html><html lang="de"><meta charset="utf-8"><title>EvidenceRFP Evaluation</title>
                <style>body{font:15px system-ui;max-width:1100px;margin:40px auto;color:#17202a}
                table{border-collapse:collapse;width:100%%}th,td{padding:8px;border:1px solid #ccd2d8;text-align:left}
                code{background:#eef2f5;padding:2px 5px}</style><h1>EvidenceRFP Qualitätsbericht</h1>
                <p>Dataset <code>%s</code>, %d Fälle. Synthetische, deterministische Baseline.</p>
                <p>Versionen: %s</p>
                <ul><li>Recall@6: %.3f</li><li>MRR: %.3f</li><li>Status Macro-F1: %.3f</li>
                <li>Required-Term-Abdeckung: %.3f</li><li>Zitationspräzision: %.3f</li>
                <li>NOT_FOUND Precision/Recall: %.3f / %.3f</li></ul>
                <table><thead><tr><th>ID</th><th>Erwartet</th><th>Ermittelt</th><th>Erster Treffer</th>
                <th>Zitationen</th><th>Fehlende Begriffe</th><th>Zitierte Quellen</th></tr></thead><tbody>%s</tbody></table></html>
                """.formatted(html(report.datasetId()), metrics.caseCount(), versions, metrics.recallAtSix(),
                metrics.meanReciprocalRank(), metrics.statusMacroF1(), metrics.requiredTermCoverage(),
                metrics.citationPrecision(), metrics.notFoundPrecision(), metrics.notFoundRecall(), rows);
        Files.writeString(outputDirectory.resolve("golden-set-report.html"), html);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.GERMAN).replaceAll("\\s+", " ").strip();
    }

    private String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }

    private static final class Confusion {
        private int tp;
        private int fp;
        private int fn;

        private double precision() { return tp + fp == 0 ? 1.0 : (double) tp / (tp + fp); }
        private double recall() { return tp + fn == 0 ? 1.0 : (double) tp / (tp + fn); }
        private double f1() {
            double precision = precision();
            double recall = recall();
            return precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        }
    }

    private record GoldenSet(String schemaVersion, String datasetId, String language, boolean synthetic,
                             String description, Map<String, String> versions,
                             List<GoldenQuestion> questions) { }
    private record GoldenQuestion(String id, String category, String question, List<String> paraphrases,
                                  boolean answerable, String expectedStatus,
                                  List<ExpectedSource> expectedSources, List<String> requiredTerms) { }
    private record ExpectedSource(String documentId, String sectionId) { }
    private record EvaluationCase(String id, String category, String question, AnswerStatus expectedStatus,
                                  List<ExpectedSource> expectedSources, List<String> requiredTerms) { }
    private record SectionStart(String id, int start, int contentStart) { }
    private record EvaluationMetrics(double recallAtSix, double meanReciprocalRank, double statusMacroF1,
                                     double requiredTermCoverage, double citationPrecision,
                                     double notFoundPrecision, double notFoundRecall, int caseCount) { }
    private record CaseResult(String id, String question, AnswerStatus expectedStatus,
                              AnswerStatus actualStatus, int firstRelevantRank, int citationCount,
                              List<String> missingTerms, List<String> citedSources,
                              List<String> citationQuotes, List<String> retrievedSources) { }
    private record EvaluationReport(String evaluatedAt, String datasetId, Map<String, String> versions,
                                    EvaluationMetrics metrics, List<CaseResult> results) { }
}
