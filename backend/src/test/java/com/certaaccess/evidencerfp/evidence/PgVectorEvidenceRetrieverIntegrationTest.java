package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import com.certaaccess.evidencerfp.analysis.application.AnalysisExporter;
import com.certaaccess.evidencerfp.analysis.application.AnalysisService;
import com.certaaccess.evidencerfp.analysis.application.DeterministicAnswerEngine;
import com.certaaccess.evidencerfp.analysis.application.EvidenceAnswerPolicy;
import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJob;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJobStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.Finding;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import com.certaaccess.evidencerfp.analysis.domain.ReviewActor;
import com.certaaccess.evidencerfp.analysis.domain.ReviewProvenance;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.application.EditConflictException;
import com.certaaccess.evidencerfp.analysis.application.IdempotencyKeyReusedException;
import com.certaaccess.evidencerfp.analysis.infrastructure.JdbcAnalysisRepository;
import com.certaaccess.evidencerfp.sample.SampleDataGenerator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;
import com.certaaccess.evidencerfp.iam.IamControlEvaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class PgVectorEvidenceRetrieverIntegrationTest {
    private static final Pattern DOCUMENT_ID = Pattern.compile("(?m)^documentId:\\s*(\\S+)\\s*$");
    private static final Pattern SECTION = Pattern.compile("(?m)^##\\s+(.+?)\\s+\\{#([^}]+)}\\s*$");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @TempDir
    Path temporaryDirectory;

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
        assertThat(embeddings.batches).isEqualTo(2);
    }

    @Test
    void fusesDenseAndGermanFullTextResultsWithProvenance() {
        PGSimpleDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();
        PgVectorEvidenceRetriever retriever = new PgVectorEvidenceRetriever(
                JdbcClient.create(dataSource), new HashingEvidenceEmbeddingProvider());
        EvidenceChunk fido = chunk("FIDO2 Sicherheitsschlüssel und WebAuthn sind phishing-resistente Faktoren");
        EvidenceChunk unrelated = chunk("Backups verbleiben im Europäischen Wirtschaftsraum");

        RetrievalResult result = retriever.retrieveWithProvenance(
                new RfpQuestion("RFP-003", "Welche FIDO2 Faktoren sind phishing-resistent?", "MFA"),
                List.of(unrelated, fido), 6);

        assertThat(result.chunks()).extracting(EvidenceChunk::id).contains(fido.id());
        RetrievedEvidence match = result.matches().stream()
                .filter(candidate -> candidate.chunk().id().equals(fido.id())).findFirst().orElseThrow();
        assertThat(match.provenance().channels()).contains(RetrievalChannel.DENSE, RetrievalChannel.FULL_TEXT);
        assertThat(match.provenance().denseRank()).isPositive();
        assertThat(match.provenance().fullTextRank()).isPositive();
        assertThat(match.provenance().retrievalVersion())
                .isEqualTo(PgVectorEvidenceRetriever.RETRIEVAL_VERSION);
        assertThat(match.provenance().chunkSha256()).hasSize(64);
    }

    @Test
    void hybridGoldenSetMeasuresBothProductionRetrievalChannels() throws IOException {
        PGSimpleDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();
        PgVectorEvidenceRetriever retriever = new PgVectorEvidenceRetriever(
                JdbcClient.create(dataSource), new HashingEvidenceEmbeddingProvider());
        Path examples = Path.of("..", "examples");
        HybridGoldenSet goldenSet = new ObjectMapper().readValue(
                examples.resolve("golden-set.json").toFile(), HybridGoldenSet.class);
        List<HybridCase> cases = expand(goldenSet.questions());
        List<EvidenceChunk> chunks = markdownChunks(examples.resolve("source"));
        List<RfpQuestion> questions = cases.stream()
                .map(item -> new RfpQuestion(item.id(), item.question(), item.category())).toList();

        Map<String, RetrievalResult> results = retriever.retrieveBatchWithProvenance(questions, chunks, 6);

        int answerable = 0;
        int hits = 0;
        double reciprocalRank = 0;
        List<String> misses = new ArrayList<>();
        for (HybridCase item : cases) {
            if (!item.answerable()) continue;
            answerable++;
            List<EvidenceChunk> retrieved = results.get(item.id()).chunks();
            int rank = firstRelevantRank(retrieved, item.expectedSources());
            if (rank > 0) {
                hits++;
                reciprocalRank += 1.0 / rank;
            } else {
                misses.add(item.id() + ": " + item.question());
            }
        }
        boolean denseSeen = results.values().stream().flatMap(result -> result.matches().stream())
                .anyMatch(match -> match.provenance().channels().contains(RetrievalChannel.DENSE));
        boolean fullTextSeen = results.values().stream().flatMap(result -> result.matches().stream())
                .anyMatch(match -> match.provenance().channels().contains(RetrievalChannel.FULL_TEXT));

        assertThat(cases).hasSizeGreaterThanOrEqualTo(80);
        assertThat((double) hits / answerable)
                .as("Recall@6; nicht gefundene Fälle: %s", misses)
                .isGreaterThanOrEqualTo(0.95);
        assertThat(reciprocalRank / answerable).isGreaterThanOrEqualTo(0.85);
        assertThat(denseSeen).isTrue();
        assertThat(fullTextSeen).isTrue();
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
        Path source = Path.of(System.getProperty("user.dir"), "..", "examples", "source")
                .toAbsolutePath().normalize();
        Path fixtures = temporaryDirectory.resolve("production-e2e");
        SampleDataGenerator.generate(source, fixtures);
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
            assertThat(workbook.getSheet("IAM Findings")).isNotNull();
            assertThat(workbook.getSheet("Quellen").getLastRowNum()).isGreaterThan(5);
        }
    }

    @Test
    void concurrentReviewUsesCompareAndSetAndKeepsExactlyOneAuditEvent() throws Exception {
        PGSimpleDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcAnalysisRepository repository = repository(dataSource);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Analysis analysis = completedAnalysis();
        transaction.executeWithoutResult(ignored -> repository.save(analysis));
        int generatedEvents = analysis.reviewEvents().size();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        var first = reviewAsync(transaction, repository, analysis.id(), ReviewStatus.APPROVED, ready, start);
        var second = reviewAsync(transaction, repository, analysis.id(), ReviewStatus.CHANGES_REQUIRED, ready, start);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<Object> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

        assertThat(outcomes.stream().filter(QuestionResult.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(EditConflictException.class::isInstance)).hasSize(1);
        Analysis reloaded = repository.findById(analysis.id()).orElseThrow();
        assertThat(reloaded.questions().getFirst().revision()).isEqualTo(1);
        assertThat(reloaded.reviewEvents()).hasSize(generatedEvents + 1);
        assertThat(reloaded.reviewEvents().getLast().provenance()).isEqualTo(ReviewProvenance.HUMAN_EDITED);
    }

    @Test
    void deletedProcessingJobCannotBeResurrectedByItsFormerWorker() {
        PGSimpleDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcAnalysisRepository repository = repository(dataSource);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Instant now = Instant.parse("2026-10-18T08:00:00Z");
        Analysis queued = processingAnalysis(now);
        transaction.executeWithoutResult(ignored -> repository.enqueue(queued,
                AnalysisJob.queued(queued.id(), now, 2), List.of(), null, "a".repeat(64)));
        assertThat(repository.claimJob(queued.id(), "worker-a", now, now.plusSeconds(30))).isPresent();

        Boolean deleted = transaction.execute(ignored -> repository.deleteById(queued.id()));
        assertThat(deleted).isTrue();
        Analysis completed = new Analysis(queued.id(), queued.customerName(), false,
                AnalysisStatus.COMPLETED, queued.createdAt(), now.plusSeconds(5), List.of(), List.of(), List.of());

        Boolean completedAfterDelete = transaction.execute(ignored -> repository.completeJob(
                completed, "worker-a", now.plusSeconds(5)));
        assertThat(completedAfterDelete).isFalse();
        assertThat(repository.findById(queued.id())).isEmpty();
    }

    @Test
    void twoWorkersAtomicallyClaimAQueuedJobOnlyOnce() throws Exception {
        PGSimpleDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcAnalysisRepository repository = repository(dataSource);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Instant now = Instant.parse("2026-10-18T08:30:00Z");
        Analysis queued = processingAnalysis(now);
        transaction.executeWithoutResult(ignored -> repository.enqueue(queued,
                AnalysisJob.queued(queued.id(), now, 2), List.of(), null, "f".repeat(64)));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        var first = claimAsync(repository, queued.id(), "worker-a", now, ready, start);
        var second = claimAsync(repository, queued.id(), "worker-b", now, ready, start);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        var claims = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

        assertThat(claims.stream().filter(java.util.Optional::isPresent)).hasSize(1);
        assertThat(repository.findJobByAnalysisId(queued.id())).get().satisfies(job -> {
            assertThat(job.status()).isEqualTo(AnalysisJobStatus.PROCESSING);
            assertThat(job.attempt()).isEqualTo(1);
        });
    }

    @Test
    void expiredLeaseIsRecoveredAndJobCanOnlyBeFinalizedOnce() {
        PGSimpleDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcAnalysisRepository repository = repository(dataSource);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Instant now = Instant.parse("2026-10-18T09:00:00Z");
        Analysis queued = processingAnalysis(now);
        transaction.executeWithoutResult(ignored -> repository.enqueue(queued,
                AnalysisJob.queued(queued.id(), now, 2), List.of(), null, "b".repeat(64)));
        assertThat(repository.claimJob(queued.id(), "crashed-worker", now, now.plusSeconds(30)))
                .get().extracting(AnalysisJob::attempt).isEqualTo(1);
        assertThat(repository.findRunnableJobIds(now.plusSeconds(29), 10)).doesNotContain(queued.id());
        assertThat(repository.findRunnableJobIds(now.plusSeconds(31), 10)).contains(queued.id());
        assertThat(repository.claimJob(queued.id(), "recovery-worker", now.plusSeconds(31),
                now.plusSeconds(61))).get().extracting(AnalysisJob::attempt).isEqualTo(2);
        Analysis completed = new Analysis(queued.id(), queued.customerName(), false,
                AnalysisStatus.COMPLETED, queued.createdAt(), now.plusSeconds(35), List.of(), List.of(), List.of());

        Boolean finalized = transaction.execute(ignored -> repository.completeJob(
                completed, "recovery-worker", now.plusSeconds(35)));
        Boolean finalizedTwice = transaction.execute(ignored -> repository.completeJob(
                completed, "recovery-worker", now.plusSeconds(36)));
        assertThat(finalized).isTrue();
        assertThat(finalizedTwice).isFalse();
        assertThat(repository.findById(queued.id())).get()
                .extracting(Analysis::status).isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    void secondExpiredLeaseFailsTheJobInsteadOfStartingAThirdAttempt() {
        PGSimpleDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcAnalysisRepository repository = repository(dataSource);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Instant now = Instant.parse("2026-10-18T09:30:00Z");
        Analysis queued = processingAnalysis(now);
        transaction.executeWithoutResult(ignored -> repository.enqueue(queued,
                AnalysisJob.queued(queued.id(), now, 2), List.of(), null, "e".repeat(64)));

        assertThat(repository.claimJob(queued.id(), "worker-one", now, now.plusSeconds(30)))
                .get().extracting(AnalysisJob::attempt).isEqualTo(1);
        assertThat(repository.claimJob(queued.id(), "worker-two", now.plusSeconds(31),
                now.plusSeconds(61))).get().extracting(AnalysisJob::attempt).isEqualTo(2);

        List<UUID> failed = transaction.execute(
                ignored -> repository.failExhaustedJobs(now.plusSeconds(62)));

        assertThat(failed).containsExactly(queued.id());
        assertThat(repository.claimJob(queued.id(), "worker-three", now.plusSeconds(63),
                now.plusSeconds(93))).isEmpty();
        assertThat(repository.findJobByAnalysisId(queued.id())).get().satisfies(job -> {
            assertThat(job.status()).isEqualTo(AnalysisJobStatus.FAILED);
            assertThat(job.failureCode()).isEqualTo("JOB_RETRIES_EXHAUSTED");
            assertThat(job.attempt()).isEqualTo(2);
        });
        assertThat(repository.findById(queued.id())).get()
                .extracting(Analysis::status).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    void idempotencyKeyReturnsExistingAnalysisAndRejectsDifferentContent() {
        PGSimpleDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcAnalysisRepository repository = repository(dataSource);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Instant now = Instant.parse("2026-10-18T10:00:00Z");
        String key = "integration-" + UUID.randomUUID();
        Analysis first = processingAnalysis(now);
        var created = transaction.execute(ignored -> repository.enqueue(first,
                AnalysisJob.queued(first.id(), now, 2), List.of(), key, "c".repeat(64)));
        Analysis duplicate = processingAnalysis(now.plusSeconds(1));
        var reused = transaction.execute(ignored -> repository.enqueue(duplicate,
                AnalysisJob.queued(duplicate.id(), now.plusSeconds(1), 2), List.of(), key, "c".repeat(64)));

        assertThat(created).isNotNull();
        assertThat(created.created()).isTrue();
        assertThat(reused).isNotNull();
        assertThat(reused.created()).isFalse();
        assertThat(reused.analysis().id()).isEqualTo(first.id());
        assertThat(repository.findById(duplicate.id())).isEmpty();
        assertThatThrownBy(() -> transaction.execute(ignored -> repository.enqueue(
                processingAnalysis(now.plusSeconds(2)), AnalysisJob.queued(UUID.randomUUID(), now, 2),
                List.of(), key, "d".repeat(64))))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private JdbcAnalysisRepository repository(PGSimpleDataSource dataSource) {
        return new JdbcAnalysisRepository(JdbcClient.create(dataSource), new ObjectMapper(),
                new DemoAnalysisFactory());
    }

    private Analysis processingAnalysis(Instant createdAt) {
        return new Analysis(UUID.randomUUID(), "Concurrency Test", false, AnalysisStatus.PROCESSING,
                createdAt, null, List.of(), List.of(), List.of());
    }

    private Analysis completedAnalysis() {
        Instant createdAt = Instant.parse("2026-10-18T07:00:00Z");
        QuestionResult question = new QuestionResult("Q-1", "Ist MFA aktiv?", "MFA",
                "Ja, MFA ist aktiv.", AnswerStatus.SUPPORTED, ReviewStatus.DRAFT,
                List.of(), null, List.of());
        return new Analysis(UUID.randomUUID(), "Review Concurrency", false, AnalysisStatus.COMPLETED,
                createdAt, createdAt.plusSeconds(10), List.of(question), List.of(), List.of());
    }

    private CompletableFuture<Object> reviewAsync(TransactionTemplate transaction,
                                                   JdbcAnalysisRepository repository,
                                                   UUID analysisId, ReviewStatus status,
                                                   CountDownLatch ready, CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) return new IllegalStateException("Startsignal fehlt");
                return transaction.execute(ignored -> repository.updateQuestion(
                        analysisId, "Q-1", 0, null, status, Instant.now()));
            } catch (Exception exception) {
                Throwable cause = exception;
                while (cause.getCause() != null && !(cause instanceof EditConflictException)) {
                    cause = cause.getCause();
                }
                return cause;
            }
        });
    }

    private CompletableFuture<java.util.Optional<AnalysisJob>> claimAsync(
            JdbcAnalysisRepository repository, UUID analysisId, String worker, Instant now,
            CountDownLatch ready, CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) return java.util.Optional.empty();
                return repository.claimJob(analysisId, worker, now, now.plusSeconds(30));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return java.util.Optional.empty();
            }
        });
    }

    private EvidenceChunk chunk(String content) {
        return new EvidenceChunk(UUID.randomUUID(), "test.pdf", "application/pdf", "Seite 1", content, null);
    }

    private List<HybridCase> expand(List<HybridGoldenQuestion> questions) {
        List<HybridCase> result = new ArrayList<>();
        for (HybridGoldenQuestion question : questions) {
            List<String> variants = new ArrayList<>();
            variants.add(question.question());
            variants.addAll(question.paraphrases() == null ? List.of() : question.paraphrases());
            for (int index = 0; index < variants.size(); index++) {
                result.add(new HybridCase(question.id() + "-" + (index + 1), question.category(),
                        variants.get(index), question.answerable(), question.expectedSources()));
            }
        }
        return result;
    }

    private int firstRelevantRank(List<EvidenceChunk> chunks, List<HybridExpectedSource> expected) {
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
                List<HybridSectionStart> starts = new ArrayList<>();
                while (sectionMatcher.find()) {
                    starts.add(new HybridSectionStart(sectionMatcher.group(2),
                            sectionMatcher.start(), sectionMatcher.end()));
                }
                for (int index = 0; index < starts.size(); index++) {
                    HybridSectionStart current = starts.get(index);
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
        private int batches;

        @Override
        public float[] embed(String text) {
            calls++;
            return delegate.embed(text);
        }

        @Override
        public List<float[]> embedBatch(List<String> texts) {
            batches++;
            return EvidenceEmbeddingProvider.super.embedBatch(texts);
        }
    }

    private record HybridGoldenSet(String schemaVersion, String datasetId, String language,
                                   boolean synthetic, String description, Map<String, String> versions,
                                   List<HybridGoldenQuestion> questions) { }
    private record HybridGoldenQuestion(String id, String category, String question,
                                        List<String> paraphrases, boolean answerable,
                                        String expectedStatus, List<HybridExpectedSource> expectedSources,
                                        List<String> requiredTerms) { }
    private record HybridExpectedSource(String documentId, String sectionId) { }
    private record HybridCase(String id, String category, String question, boolean answerable,
                              List<HybridExpectedSource> expectedSources) { }
    private record HybridSectionStart(String id, int start, int contentStart) { }
}
