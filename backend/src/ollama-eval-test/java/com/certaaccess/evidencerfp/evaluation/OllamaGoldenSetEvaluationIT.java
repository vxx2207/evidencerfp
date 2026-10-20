package com.certaaccess.evidencerfp.evaluation;

import com.certaaccess.evidencerfp.EvidenceRfpApplication;
import com.certaaccess.evidencerfp.analysis.application.AnswerGenerator;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.Citation;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.infrastructure.OllamaAnswerGenerator;
import com.certaaccess.evidencerfp.evidence.CitationVerifier;
import com.certaaccess.evidencerfp.evidence.EvidenceChunk;
import com.certaaccess.evidencerfp.evidence.EvidenceChunkIdentity;
import com.certaaccess.evidencerfp.evidence.EvidenceEmbeddingProvider;
import com.certaaccess.evidencerfp.evidence.OllamaEvidenceEmbeddingProvider;
import com.certaaccess.evidencerfp.evidence.PgVectorEvidenceRetriever;
import com.certaaccess.evidencerfp.evidence.RetrievalChannel;
import com.certaaccess.evidencerfp.evidence.RetrievalResult;
import com.certaaccess.evidencerfp.evidence.RfpQuestion;
import com.certaaccess.evidencerfp.evidence.RetrievedEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bewusst teurer, echter Provider-Lauf. Die zusätzliche Umgebungsvariable verhindert, dass das
 * Profil versehentlich Modelle lädt oder einen normalen CI-Build vom lokalen Ollama abhängig macht.
 */
class OllamaGoldenSetEvaluationIT {
    private static final Pattern DOCUMENT_ID = Pattern.compile("(?m)^documentId:\\s*(\\S+)\\s*$");
    private static final Pattern SECTION = Pattern.compile("(?m)^##\\s+(.+?)\\s+\\{#([^}]+)}\\s*$");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RETRIEVAL_VERSION = PgVectorEvidenceRetriever.RETRIEVAL_VERSION;
    private static final String PROMPT_VERSION = "claims-v1";

    @Test
    @EnabledIfEnvironmentVariable(named = "EVIDENCERFP_RUN_OLLAMA_EVAL", matches = "(?i:true|1)")
    void evaluatesProductionHybridRetrievalAndRealOllamaProviders() throws Exception {
        Path examples = Path.of("..", "examples").toAbsolutePath().normalize();
        GoldenSet goldenSet = JSON.readValue(examples.resolve("golden-set.json").toFile(), GoldenSet.class);
        List<EvaluationCase> cases = expand(goldenSet.questions());
        List<EvidenceChunk> chunks = markdownChunks(examples.resolve("source"));
        assertThat(cases).hasSizeGreaterThanOrEqualTo(80);
        assertThat(chunks).isNotEmpty();

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))) {
            postgres.start();
            try (ConfigurableApplicationContext context = application(postgres)) {
                PgVectorEvidenceRetriever retriever = context.getBean(PgVectorEvidenceRetriever.class);
                EvidenceEmbeddingProvider embeddings = context.getBean(EvidenceEmbeddingProvider.class);
                AnswerGenerator answerGenerator = context.getBean(AnswerGenerator.class);
                CitationVerifier citationVerifier = context.getBean(CitationVerifier.class);
                Environment environment = context.getEnvironment();

                assertThat(embeddings).isInstanceOf(OllamaEvidenceEmbeddingProvider.class);
                assertThat(answerGenerator).isInstanceOf(OllamaAnswerGenerator.class);

                Instant startedAt = Instant.now();
                long retrievalStarted = System.nanoTime();
                Map<String, RetrievalResult> retrieval = retriever.retrieveBatchWithProvenance(
                        cases.stream().map(EvaluationCase::asQuestion).toList(), chunks, 6);
                Duration retrievalDuration = Duration.ofNanos(System.nanoTime() - retrievalStarted);

                EvaluationReport report = evaluate(goldenSet, cases, retrieval, answerGenerator,
                        citationVerifier, startedAt, retrievalDuration,
                        environment.getProperty("evidence-rfp.ai.chat-model", "llama3.1:8b"),
                        embeddings.modelId(), embeddings.getClass().getName(),
                        answerGenerator.getClass().getName());
                List<Path> artifacts = writeReports(report, outputDirectory());

                assertThat(report.metrics().denseResultCount()).isPositive();
                assertThat(report.metrics().fullTextResultCount()).isPositive();
                assertThat(report.gatesPassed())
                        .as("Echter Ollama-Lauf unterschreitet Qualitätsgates; Bericht: %s", artifacts)
                        .isTrue();
            }
        }
    }

    private ConfigurableApplicationContext application(PostgreSQLContainer<?> postgres) {
        return new SpringApplicationBuilder(EvidenceRfpApplication.class)
                .web(WebApplicationType.NONE)
                // Command-line properties deliberately outrank application-production.yaml and
                // host environment variables, so the evaluation can only use its own container.
                .run("--spring.profiles.active=production,ollama",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--spring.main.banner-mode=off",
                        "--evidence-rfp.analysis.recovery-initial-delay-ms=86400000",
                        "--evidence-rfp.analysis.recovery-interval-ms=86400000");
    }

    private EvaluationReport evaluate(GoldenSet goldenSet, List<EvaluationCase> cases,
                                      Map<String, RetrievalResult> retrieval,
                                      AnswerGenerator answerGenerator, CitationVerifier citationVerifier,
                                      Instant startedAt, Duration retrievalDuration,
                                      String chatModel, String embeddingModel,
                                      String embeddingAdapter, String chatAdapter) {
        List<CaseResult> results = new ArrayList<>();
        Map<AnswerStatus, Confusion> confusion = new EnumMap<>(AnswerStatus.class);
        for (AnswerStatus status : AnswerStatus.values()) confusion.put(status, new Confusion());
        int answerable = 0;
        int retrievalHits = 0;
        double reciprocalRankSum = 0;
        int requiredTerms = 0;
        int coveredTerms = 0;
        int citations = 0;
        int validCitations = 0;
        int denseResults = 0;
        int fullTextResults = 0;
        long generationNanos = 0;

        for (EvaluationCase evaluationCase : cases) {
            RetrievalResult retrievalResult = retrieval.get(evaluationCase.id());
            assertThat(retrievalResult).as("Retrieval-Ergebnis für %s", evaluationCase.id()).isNotNull();
            List<EvidenceChunk> retrieved = retrievalResult.chunks();
            denseResults += countChannel(retrievalResult, RetrievalChannel.DENSE);
            fullTextResults += countChannel(retrievalResult, RetrievalChannel.FULL_TEXT);
            int firstRelevantRank = firstRelevantRank(retrieved, evaluationCase.expectedSources());
            if (evaluationCase.expectedStatus() != AnswerStatus.NOT_FOUND) {
                answerable++;
                if (firstRelevantRank > 0) {
                    retrievalHits++;
                    reciprocalRankSum += 1.0 / firstRelevantRank;
                }
            }

            long generationStarted = System.nanoTime();
            QuestionResult answer = answerGenerator.generate(evaluationCase.asQuestion(), retrieved);
            long caseGenerationNanos = System.nanoTime() - generationStarted;
            generationNanos += caseGenerationNanos;

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
                    answer.answer(), answer.internalClarification(), answer.citations().size(), missingTerms,
                    answer.citations().stream()
                            .map(citation -> citation.documentName() + "/" + citation.locator()).toList(),
                    answer.citations().stream().map(Citation::quote).toList(),
                    Duration.ofNanos(caseGenerationNanos).toMillis(),
                    retrievalResult.matches().stream().map(this::retrievalMatch).toList()));
        }

        double macroF1 = confusion.values().stream().mapToDouble(Confusion::f1).average().orElse(0);
        Confusion notFound = confusion.get(AnswerStatus.NOT_FOUND);
        EvaluationMetrics metrics = new EvaluationMetrics(
                ratio(retrievalHits, answerable), answerable == 0 ? 0 : reciprocalRankSum / answerable,
                macroF1, ratio(coveredTerms, requiredTerms), ratio(validCitations, citations),
                notFound.precision(), notFound.recall(), cases.size(), denseResults, fullTextResults,
                retrievalDuration.toMillis(), Duration.ofNanos(generationNanos).toMillis());
        QualityGates gates = new QualityGates(80, 0.95, 0.85, 0.90, 0.95, 1.0, 1.0, 1.0);
        Map<String, String> versions = new LinkedHashMap<>(goldenSet.versions());
        versions.put("dataset", goldenSet.datasetId());
        versions.put("evaluatedRetriever", RETRIEVAL_VERSION);
        versions.put("evaluatedPrompt", PROMPT_VERSION);
        versions.put("chatModel", chatModel);
        versions.put("embeddingModel", embeddingModel);
        ProviderRuntime providers = new ProviderRuntime("ollama-live", chatAdapter, embeddingAdapter,
                chatModel, embeddingModel);
        return new EvaluationReport(startedAt.toString(), Instant.now().toString(), true,
                goldenSet.datasetId(), versions, providers, gates, metrics,
                gates.passes(metrics), results);
    }

    private int countChannel(RetrievalResult result, RetrievalChannel channel) {
        return (int) result.matches().stream()
                .filter(match -> match.provenance().channels().contains(channel)).count();
    }

    private RetrievalMatch retrievalMatch(RetrievedEvidence match) {
        return new RetrievalMatch(match.chunk().documentName(), match.chunk().locator(),
                match.provenance().channels(), match.provenance().denseRank(),
                match.provenance().fullTextRank(), match.provenance().fusedScore(),
                match.provenance().chunkSha256());
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
                    starts.add(new SectionStart(sectionMatcher.group(2),
                            sectionMatcher.start(), sectionMatcher.end()));
                }
                for (int index = 0; index < starts.size(); index++) {
                    SectionStart current = starts.get(index);
                    int end = index + 1 < starts.size() ? starts.get(index + 1).start() : markdown.length();
                    String content = markdown.substring(current.contentStart(), end).trim();
                    String documentHash = EvidenceChunkIdentity.sha256(markdown);
                    chunks.add(new EvidenceChunk(
                            EvidenceChunkIdentity.stableId(documentHash, current.id(), content),
                            documentId, "text/markdown", current.id(), content, null));
                }
            }
        }
        return chunks;
    }

    private Path outputDirectory() {
        return Path.of(System.getProperty("evidencerfp.eval.outputDirectory",
                "target/evaluation/ollama")).toAbsolutePath().normalize();
    }

    private List<Path> writeReports(EvaluationReport report, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        String artifactStem = sanitize(report.datasetId()) + "__" + sanitize(RETRIEVAL_VERSION)
                + "__" + sanitize(report.providers().chatModel()) + "__"
                + sanitize(report.providers().embeddingModel());
        Path json = outputDirectory.resolve(artifactStem + ".json");
        Path html = outputDirectory.resolve(artifactStem + ".html");
        Files.writeString(json, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report),
                StandardCharsets.UTF_8);
        Files.writeString(html, renderHtml(report), StandardCharsets.UTF_8);
        return List.of(json, html);
    }

    private String renderHtml(EvaluationReport report) {
        String rows = report.results().stream().map(result -> "<tr><td>" + html(result.id())
                        + "</td><td>" + html(result.expectedStatus().name()) + "</td><td>"
                        + html(result.actualStatus().name()) + "</td><td>" + result.firstRelevantRank()
                        + "</td><td>" + result.citationCount() + "</td><td>"
                        + result.generationMillis() + " ms</td><td>"
                        + html(String.join(", ", result.missingTerms())) + "</td><td>"
                        + html(String.join(", ", result.citedSources())) + "</td></tr>")
                .reduce("", String::concat);
        EvaluationMetrics metrics = report.metrics();
        String versions = report.versions().entrySet().stream()
                .map(entry -> html(entry.getKey()) + "=<code>" + html(entry.getValue()) + "</code>")
                .reduce((left, right) -> left + " · " + right).orElse("");
        return """
                <!doctype html><html lang="de"><meta charset="utf-8"><title>EvidenceRFP Ollama-Evaluation</title>
                <style>body{font:15px system-ui;max-width:1180px;margin:40px auto;color:#17202a}
                table{border-collapse:collapse;width:100%%}th,td{padding:8px;border:1px solid #ccd2d8;text-align:left}
                code{background:#eef2f5;padding:2px 5px}.ok{color:#08783f}.fail{color:#b42318}</style>
                <h1>EvidenceRFP · echter Ollama-/Hybrid-RAG-Lauf</h1>
                <p><strong>Nur synthetische Daten.</strong> Dataset <code>%s</code>, %d Fälle.</p>
                <p>Versionen: %s</p><p>Provider: <code>%s</code> / <code>%s</code></p>
                <h2 class="%s">Qualitätsgates: %s</h2>
                <ul><li>Recall@6: %.3f</li><li>MRR: %.3f</li><li>Status Macro-F1: %.3f</li>
                <li>Required-Term-Abdeckung: %.3f</li><li>Zitationspräzision: %.3f</li>
                <li>NOT_FOUND Precision/Recall: %.3f / %.3f</li>
                <li>Dense-/FTS-Ergebnisbeiträge: %d / %d</li>
                <li>Retrieval / Generierung: %d ms / %d ms</li></ul>
                <table><thead><tr><th>ID</th><th>Erwartet</th><th>Ermittelt</th><th>Erster Treffer</th>
                <th>Zitationen</th><th>Generierung</th><th>Fehlende Begriffe</th><th>Zitierte Quellen</th>
                </tr></thead><tbody>%s</tbody></table></html>
                """.formatted(html(report.datasetId()), metrics.caseCount(), versions,
                html(report.providers().chatModel()), html(report.providers().embeddingModel()),
                report.gatesPassed() ? "ok" : "fail", report.gatesPassed() ? "BESTANDEN" : "NICHT BESTANDEN",
                metrics.recallAtSix(), metrics.meanReciprocalRank(), metrics.statusMacroF1(),
                metrics.requiredTermCoverage(), metrics.citationPrecision(), metrics.notFoundPrecision(),
                metrics.notFoundRecall(), metrics.denseResultCount(), metrics.fullTextResultCount(),
                metrics.retrievalMillis(), metrics.generationMillis(), rows);
    }

    private String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.GERMAN).replaceAll("\\s+", " ").strip();
    }

    private String html(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
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
                                  List<ExpectedSource> expectedSources, List<String> requiredTerms) {
        private RfpQuestion asQuestion() { return new RfpQuestion(id, question, category); }
    }
    private record SectionStart(String id, int start, int contentStart) { }
    private record ProviderRuntime(String mode, String chatAdapter, String embeddingAdapter,
                                   String chatModel, String embeddingModel) { }
    private record QualityGates(int minimumCases, double minimumRecallAtSix,
                                double minimumMeanReciprocalRank, double minimumStatusMacroF1,
                                double minimumRequiredTermCoverage, double requiredCitationPrecision,
                                double requiredNotFoundPrecision, double requiredNotFoundRecall) {
        private boolean passes(EvaluationMetrics metrics) {
            return metrics.caseCount() >= minimumCases
                    && metrics.recallAtSix() >= minimumRecallAtSix
                    && metrics.meanReciprocalRank() >= minimumMeanReciprocalRank
                    && metrics.statusMacroF1() >= minimumStatusMacroF1
                    && metrics.requiredTermCoverage() >= minimumRequiredTermCoverage
                    && metrics.citationPrecision() == requiredCitationPrecision
                    && metrics.notFoundPrecision() == requiredNotFoundPrecision
                    && metrics.notFoundRecall() == requiredNotFoundRecall
                    && metrics.denseResultCount() > 0 && metrics.fullTextResultCount() > 0;
        }
    }
    private record EvaluationMetrics(double recallAtSix, double meanReciprocalRank,
                                     double statusMacroF1, double requiredTermCoverage,
                                     double citationPrecision, double notFoundPrecision,
                                     double notFoundRecall, int caseCount, int denseResultCount,
                                     int fullTextResultCount, long retrievalMillis,
                                     long generationMillis) { }
    private record RetrievalMatch(String documentId, String sectionId, Set<RetrievalChannel> channels,
                                  Integer denseRank, Integer fullTextRank, double fusedScore,
                                  String chunkSha256) { }
    private record CaseResult(String id, String question, AnswerStatus expectedStatus,
                              AnswerStatus actualStatus, int firstRelevantRank, String answer,
                              String internalClarification, int citationCount, List<String> missingTerms,
                              List<String> citedSources, List<String> citationQuotes,
                              long generationMillis, List<RetrievalMatch> retrieval) { }
    private record EvaluationReport(String startedAt, String completedAt, boolean synthetic,
                                    String datasetId, Map<String, String> versions,
                                    ProviderRuntime providers, QualityGates gates,
                                    EvaluationMetrics metrics, boolean gatesPassed,
                                    List<CaseResult> results) { }
}
