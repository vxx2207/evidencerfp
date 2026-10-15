package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.analysis.application.DeterministicAnswerEngine;
import com.certaaccess.evidencerfp.analysis.application.EvidenceAnswerPolicy;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
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
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenSetEvaluationTest {
    private static final Pattern DOCUMENT_ID = Pattern.compile("(?m)^documentId:\\s*(\\S+)\\s*$");
    private static final Pattern SECTION = Pattern.compile("(?m)^##\\s+(.+?)\\s+\\{#([^}]+)}\\s*$");

    @Test
    void findsTheExpectedSourceForAtLeastNinetyPercentOfAnswerableQuestions() throws IOException {
        Path examples = Path.of("..", "examples");
        GoldenSet goldenSet = new ObjectMapper().readValue(
                examples.resolve("golden-set.json").toFile(), GoldenSet.class);
        List<EvidenceChunk> chunks = markdownChunks(examples.resolve("source"));
        LexicalEvidenceRetriever retriever = new LexicalEvidenceRetriever();
        List<String> missed = new ArrayList<>();
        int answerable = 0;
        int hits = 0;

        for (GoldenQuestion question : goldenSet.questions()) {
            if (!question.answerable()) continue;
            answerable++;
            List<EvidenceChunk> result = retriever.retrieve(
                    new RfpQuestion(question.id(), question.question(), question.category()), chunks, 6);
            boolean hit = question.expectedSources().stream().anyMatch(expected -> result.stream()
                    .anyMatch(chunk -> chunk.documentName().equals(expected.documentId())
                            && chunk.locator().equals(expected.sectionId())));
            if (hit) hits++; else missed.add(question.id());
        }

        double recallAtSix = (double) hits / answerable;
        System.out.printf("Golden Set Recall@6: %.1f%% (%d/%d)%n", recallAtSix * 100, hits, answerable);
        assertThat(recallAtSix)
                .withFailMessage("Recall@6 %.1f%%; verfehlt: %s", recallAtSix * 100, missed)
                .isGreaterThanOrEqualTo(0.90);
    }

    @Test
    void returnsNotFoundForEveryUnanswerableGoldenQuestion() throws IOException {
        Path examples = Path.of("..", "examples");
        GoldenSet goldenSet = new ObjectMapper().readValue(
                examples.resolve("golden-set.json").toFile(), GoldenSet.class);
        List<EvidenceChunk> chunks = markdownChunks(examples.resolve("source"));
        LexicalEvidenceRetriever retriever = new LexicalEvidenceRetriever();
        DeterministicAnswerEngine answerEngine = new DeterministicAnswerEngine(
                new CitationVerifier(), new EvidenceAnswerPolicy(
                        Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC), 365));

        for (GoldenQuestion question : goldenSet.questions()) {
            if (question.answerable()) continue;
            RfpQuestion rfpQuestion = new RfpQuestion(question.id(), question.question(), question.category());
            var result = answerEngine.generate(rfpQuestion, retriever.retrieve(rfpQuestion, chunks, 6));
            assertThat(result.answerStatus()).as(question.id()).isEqualTo(AnswerStatus.NOT_FOUND);
            assertThat(result.citations()).as(question.id()).isEmpty();
        }
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
                    String stableId = documentId + "#" + current.id();
                    chunks.add(new EvidenceChunk(UUID.nameUUIDFromBytes(stableId.getBytes(StandardCharsets.UTF_8)),
                            documentId, "text/markdown", current.id(), content, null));
                }
            }
        }
        return chunks;
    }

    private record GoldenSet(List<GoldenQuestion> questions) { }
    private record GoldenQuestion(String id, String category, String question, boolean answerable,
                                  List<ExpectedSource> expectedSources) { }
    private record ExpectedSource(String documentId, String sectionId) { }
    private record SectionStart(String id, int start, int contentStart) { }
}
