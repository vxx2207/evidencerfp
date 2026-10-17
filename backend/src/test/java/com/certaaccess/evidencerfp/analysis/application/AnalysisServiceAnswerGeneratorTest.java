package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.EvidenceDocument;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import com.certaaccess.evidencerfp.evidence.EvidenceChunk;
import com.certaaccess.evidencerfp.evidence.EvidenceExtractor;
import com.certaaccess.evidencerfp.evidence.EvidenceRetriever;
import com.certaaccess.evidencerfp.evidence.ExtractedEvidence;
import com.certaaccess.evidencerfp.evidence.RfpParser;
import com.certaaccess.evidencerfp.evidence.RfpQuestion;
import com.certaaccess.evidencerfp.evidence.UploadValidator;
import com.certaaccess.evidencerfp.iam.IamControlEvaluator;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisServiceAnswerGeneratorTest {

    @Test
    void routesRetrievedChunksThroughAnswerGeneratorPort() {
        UploadValidator validator = mock(UploadValidator.class);
        RfpParser parser = mock(RfpParser.class);
        EvidenceExtractor extractor = mock(EvidenceExtractor.class);
        MockMultipartFile rfp = new MockMultipartFile("rfp", "rfp.xlsx", "application/x-test", new byte[]{1});
        MockMultipartFile evidence = new MockMultipartFile("evidence", "source.pdf", "application/pdf", new byte[]{2});
        RfpQuestion question = new RfpQuestion("RFP-001", "Wird FIDO2 unterstützt?", "Identität");
        EvidenceChunk chunk = new EvidenceChunk(UUID.randomUUID(), "source.pdf", "application/pdf", "Seite 1",
                "FIDO2 wird unterstützt.", null);
        when(parser.parse(any(MultipartFile.class))).thenReturn(List.of(question));
        when(extractor.extract(any(MultipartFile.class))).thenReturn(new ExtractedEvidence(
                new EvidenceDocument("source.pdf", "application/pdf", 1, null, false), List.of(chunk)));
        RecordingRetriever retriever = new RecordingRetriever();
        RecordingGenerator generator = new RecordingGenerator();
        InMemoryRepository repository = new InMemoryRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);
        AnalysisService service = new AnalysisService(repository, validator, parser, extractor, retriever,
                generator, new IamControlEvaluator(clock), new ObjectMapper(), clock);

        Analysis accepted = service.create("Lokaler Test", rfp, List.of(evidence), null);
        Analysis result = service.get(accepted.id());

        assertThat(accepted.status()).isEqualTo(AnalysisStatus.PROCESSING);
        assertThat(result.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.questions()).singleElement().satisfies(answer -> {
            assertThat(answer.answer()).isEqualTo("Vom Fake-Generator belegt.");
            assertThat(answer.answerStatus()).isEqualTo(AnswerStatus.SUPPORTED);
        });
        assertThat(retriever.called).isTrue();
        assertThat(generator.received).containsExactly(chunk);
        assertThat(result.evidenceDocuments()).singleElement().satisfies(document ->
                assertThat(document.passages()).singleElement().satisfies(passage -> {
                    assertThat(passage.chunkId()).isEqualTo(chunk.id());
                    assertThat(passage.locator()).isEqualTo("Seite 1");
                    assertThat(passage.content()).isEqualTo("FIDO2 wird unterstützt.");
                }));
        assertThat(result.reviewEvents()).singleElement().satisfies(event -> {
            assertThat(event.questionId()).isEqualTo("RFP-001");
            assertThat(event.provenance().name()).isEqualTo("GENERATED");
            assertThat(event.occurredAt()).isEqualTo(clock.instant());
        });
    }

    private static final class RecordingRetriever implements EvidenceRetriever {
        private boolean called;

        @Override
        public List<EvidenceChunk> retrieve(RfpQuestion question, List<EvidenceChunk> availableChunks, int limit) {
            called = true;
            return availableChunks;
        }
    }

    private static final class RecordingGenerator implements AnswerGenerator {
        private List<EvidenceChunk> received;

        @Override
        public QuestionResult generate(RfpQuestion question, List<EvidenceChunk> retrievedChunks) {
            received = retrievedChunks;
            return new QuestionResult(question.id(), question.question(), question.category(),
                    "Vom Fake-Generator belegt.", AnswerStatus.SUPPORTED, ReviewStatus.DRAFT,
                    List.of(), null, List.of());
        }
    }

    private static final class InMemoryRepository implements AnalysisRepository {
        private final Map<UUID, Analysis> values = new ConcurrentHashMap<>();

        @Override public Analysis save(Analysis analysis) { values.put(analysis.id(), analysis); return analysis; }
        @Override public Optional<Analysis> findById(UUID id) { return Optional.ofNullable(values.get(id)); }
        @Override public boolean deleteById(UUID id) { return values.remove(id) != null; }
    }
}
