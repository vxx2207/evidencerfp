package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import com.certaaccess.evidencerfp.analysis.application.AnalysisExporter;
import com.certaaccess.evidencerfp.analysis.application.AnalysisService;
import com.certaaccess.evidencerfp.analysis.application.DeterministicAnswerEngine;
import com.certaaccess.evidencerfp.analysis.application.EvidenceAnswerPolicy;
import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.Finding;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import com.certaaccess.evidencerfp.analysis.domain.ReviewActor;
import com.certaaccess.evidencerfp.analysis.domain.ReviewProvenance;
import com.certaaccess.evidencerfp.analysis.infrastructure.JdbcAnalysisRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import com.certaaccess.evidencerfp.iam.IamControlEvaluator;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PgVectorEvidenceRetrieverIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Test
    void migratesSchemaAndRetrievesNearestChunkWithPgvectorOperator() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        PgVectorEvidenceRetriever retriever = new PgVectorEvidenceRetriever(
                JdbcClient.create(dataSource), new HashingEvidenceEmbeddingProvider());
        EvidenceChunk relevant = chunk("FIDO2 Passkeys phishing-resistente Authentifizierung");
        EvidenceChunk unrelated = chunk("Backups und Wiederherstellung werden jährlich getestet");

        List<EvidenceChunk> result = retriever.retrieve(
                new RfpQuestion("RFP-001", "Werden FIDO2 Passkeys unterstützt?", "Identität"),
                List.of(unrelated, relevant), 1);

        assertThat(result).extracting(EvidenceChunk::id).containsExactly(relevant.id());
    }

    @Test
    void embedsTheCorpusOnlyOnceForABatchOfQuestions() {
        PGSimpleDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();
        CountingEmbeddingProvider embeddings = new CountingEmbeddingProvider();
        PgVectorEvidenceRetriever retriever = new PgVectorEvidenceRetriever(
                JdbcClient.create(dataSource), embeddings);
        EvidenceChunk authentication = chunk("FIDO2 Passkeys phishing-resistente Authentifizierung");
        EvidenceChunk backup = chunk("Backups und Wiederherstellung werden jährlich getestet");
        List<RfpQuestion> questions = List.of(
                new RfpQuestion("RFP-001", "Werden FIDO2 Passkeys unterstützt?", "Identität"),
                new RfpQuestion("RFP-002", "Wie werden Backups getestet?", "Betrieb"));

        Map<String, List<EvidenceChunk>> results = retriever.retrieveBatch(
                questions, List.of(authentication, backup), 1);

        assertThat(results.get("RFP-001")).extracting(EvidenceChunk::id)
                .containsExactly(authentication.id());
        assertThat(results.get("RFP-002")).extracting(EvidenceChunk::id)
                .containsExactly(backup.id());
        assertThat(embeddings.calls).isEqualTo(4);
    }

    @Test
    void persistsAndReloadsTheCompleteReviewState() {
        PGSimpleDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcAnalysisRepository repository = new JdbcAnalysisRepository(
                JdbcClient.create(dataSource), new ObjectMapper(), new DemoAnalysisFactory());
        Analysis analysis = new DemoAnalysisFactory().create();
        analysis.updateQuestion("RFP-001", null, ReviewStatus.APPROVED);

        repository.save(analysis);
        Analysis reloaded = repository.findById(analysis.id()).orElseThrow();

        assertThat(reloaded.questions()).hasSize(24);
        assertThat(reloaded.evidenceDocuments()).hasSize(5);
        assertThat(reloaded.evidenceDocuments().getFirst().passages()).isNotEmpty();
        assertThat(reloaded.evidenceDocuments().getFirst().passages())
                .extracting(com.certaaccess.evidencerfp.analysis.domain.EvidencePassage::content)
                .anyMatch(content -> content.contains("SAML 2.0"));
        assertThat(reloaded.findings()).hasSize(6);
        assertThat(reloaded.questions().getFirst().reviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(reloaded.reviewEvents()).hasSize(25);
        assertThat(reloaded.reviewEvents().getLast()).satisfies(event -> {
            assertThat(event.actor()).isEqualTo(ReviewActor.LOCAL_REVIEWER);
            assertThat(event.provenance()).isEqualTo(ReviewProvenance.HUMAN_EDITED);
            assertThat(event.beforeReviewStatus()).isEqualTo(ReviewStatus.DRAFT);
            assertThat(event.afterReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        });

        repository.save(reloaded);
        assertThat(repository.findById(analysis.id()).orElseThrow().reviewEvents()).hasSize(25);
    }

    @Test
    void runsProductionImportReviewPersistenceAndXlsxExportEndToEnd() throws Exception {
        PGSimpleDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        Clock clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
        JdbcAnalysisRepository repository = new JdbcAnalysisRepository(
                jdbc, new ObjectMapper(), new DemoAnalysisFactory());
        AnalysisService service = new AnalysisService(
                repository,
                new UploadValidator(new UploadValidationProperties(10_485_760, 52_428_800, 20)),
                new RfpParser(),
                new EvidenceExtractor(clock, 365),
                new PgVectorEvidenceRetriever(jdbc, new HashingEvidenceEmbeddingProvider()),
                new DeterministicAnswerEngine(new CitationVerifier(), new EvidenceAnswerPolicy(clock, 365)),
                new IamControlEvaluator(clock), new ObjectMapper(), clock, Runnable::run);
        Path fixtures = Path.of(System.getProperty("user.dir"), "..", "examples", "generated")
                .toAbsolutePath().normalize();
        MultipartFile rfp = multipart(fixtures.resolve("rfp-security-questionnaire.xlsx"));
        List<MultipartFile> evidence;
        try (var files = Files.list(fixtures)) {
            evidence = files.filter(path -> path.toString().endsWith(".pdf")
                            || path.toString().endsWith(".docx"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> {
                        try {
                            return multipart(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
        }

        Analysis accepted = service.create("Production E2E", rfp, evidence, null);
        Analysis completed = service.get(accepted.id());
        assertThat(completed.questions()).hasSize(24);
        assertThat(completed.evidenceDocuments()).hasSize(5)
                .allMatch(document -> !document.passages().isEmpty());

        for (var question : completed.questions()) {
            ReviewStatus decision = question.answerStatus() == AnswerStatus.NOT_FOUND
                    || question.findings().stream().anyMatch(Finding::blocksApproval)
                    ? ReviewStatus.CHANGES_REQUIRED : ReviewStatus.APPROVED;
            service.updateQuestion(completed.id(), question.id(), null, decision);
        }
        Analysis reviewed = service.get(completed.id());
        assertThat(reviewed.reviewComplete()).isTrue();
        assertThat(reviewed.reviewEvents()).hasSizeGreaterThanOrEqualTo(48);
        assertThat(jdbc.sql("SELECT count(*) FROM evidence_retrieval_chunk")
                .query(Long.class).single()).isZero();

        byte[] export = new AnalysisExporter().toXlsx(reviewed);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(export))) {
            assertThat(workbook.getSheet("Antworten").getLastRowNum()).isEqualTo(24);
            assertThat(workbook.getSheet("Review").getLastRowNum())
                    .isEqualTo(reviewed.reviewEvents().size());
            assertThat(workbook.getSheet("Evidenzregister").getLastRowNum()).isEqualTo(5);
        }
    }

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private EvidenceChunk chunk(String content) {
        return new EvidenceChunk(UUID.randomUUID(), "test.pdf", "application/pdf", "Seite 1", content, null);
    }

    private MultipartFile multipart(Path path) throws IOException {
        String name = path.getFileName().toString();
        String mediaType = name.endsWith(".xlsx")
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : name.endsWith(".pdf") ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return new MockMultipartFile(name.endsWith(".xlsx") ? "rfp" : "evidence",
                name, mediaType, Files.readAllBytes(path));
    }

    private static final class CountingEmbeddingProvider implements EvidenceEmbeddingProvider {
        private final HashingEvidenceEmbeddingProvider delegate = new HashingEvidenceEmbeddingProvider();
        private int calls;

        @Override
        public float[] embed(String text) {
            calls++;
            return delegate.embed(text);
        }
    }
}
