package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJob;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJobStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisPhase;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisUpload;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisUploadKind;
import com.certaaccess.evidencerfp.analysis.domain.EvidenceDocument;
import com.certaaccess.evidencerfp.analysis.domain.EvidencePassage;
import com.certaaccess.evidencerfp.analysis.domain.Finding;
import com.certaaccess.evidencerfp.analysis.domain.FindingSeverity;
import com.certaaccess.evidencerfp.analysis.domain.FindingType;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import com.certaaccess.evidencerfp.analysis.infrastructure.AnalysisUploadStorage;
import com.certaaccess.evidencerfp.evidence.EvidenceChunk;
import com.certaaccess.evidencerfp.evidence.EvidenceExtractor;
import com.certaaccess.evidencerfp.evidence.EvidenceRetriever;
import com.certaaccess.evidencerfp.evidence.EvidenceProviderException;
import com.certaaccess.evidencerfp.evidence.ExtractedEvidence;
import com.certaaccess.evidencerfp.evidence.InvalidUploadException;
import com.certaaccess.evidencerfp.evidence.RetrievalResult;
import com.certaaccess.evidencerfp.evidence.RfpParser;
import com.certaaccess.evidencerfp.evidence.RfpQuestion;
import com.certaaccess.evidencerfp.evidence.UploadValidator;
import com.certaaccess.evidencerfp.iam.IamControlEvaluator;
import com.certaaccess.evidencerfp.iam.IamSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AnalysisService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisService.class);
    private static final String GENERIC_FAILURE_CODE = "ANALYSIS_PROCESSING_FAILED";
    private static final String CLEANUP_FAILURE_CODE = "UPLOAD_CLEANUP_FAILED";
    private static final Duration CLEANUP_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration ORPHAN_GRACE_PERIOD = Duration.ofHours(1);

    private final AnalysisRepository repository;
    private final UploadValidator uploadValidator;
    private final RfpParser rfpParser;
    private final EvidenceExtractor evidenceExtractor;
    private final EvidenceRetriever evidenceRetriever;
    private final AnswerGenerator answerGenerator;
    private final IamControlEvaluator iamControlEvaluator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TaskExecutor taskExecutor;
    private final TaskScheduler heartbeatScheduler;
    private final AnalysisUploadStorage uploadStorage;
    private final Duration leaseDuration;
    private final Duration heartbeatInterval;
    private final int maxAttempts;
    private final int recoveryBatchSize;
    private final String workerInstanceId = UUID.randomUUID().toString();

    AnalysisService(AnalysisRepository repository, UploadValidator uploadValidator, RfpParser rfpParser,
                    EvidenceExtractor evidenceExtractor, EvidenceRetriever evidenceRetriever,
                    AnswerGenerator answerGenerator, IamControlEvaluator iamControlEvaluator,
                    ObjectMapper objectMapper, Clock clock) {
        this(repository, uploadValidator, rfpParser, evidenceExtractor, evidenceRetriever,
                answerGenerator, iamControlEvaluator, objectMapper, clock, Runnable::run, null,
                testUploadStorage(), Duration.ofSeconds(30), Duration.ofSeconds(10), 2, 8);
    }

    public AnalysisService(AnalysisRepository repository, UploadValidator uploadValidator, RfpParser rfpParser,
                           EvidenceExtractor evidenceExtractor, EvidenceRetriever evidenceRetriever,
                           AnswerGenerator answerGenerator, IamControlEvaluator iamControlEvaluator,
                           ObjectMapper objectMapper, Clock clock, TaskExecutor taskExecutor) {
        this(repository, uploadValidator, rfpParser, evidenceExtractor, evidenceRetriever,
                answerGenerator, iamControlEvaluator, objectMapper, clock, taskExecutor, null,
                testUploadStorage(), Duration.ofSeconds(30), Duration.ofSeconds(10), 2, 8);
    }

    @Autowired
    public AnalysisService(AnalysisRepository repository, UploadValidator uploadValidator, RfpParser rfpParser,
                           EvidenceExtractor evidenceExtractor, EvidenceRetriever evidenceRetriever,
                           AnswerGenerator answerGenerator, IamControlEvaluator iamControlEvaluator,
                           ObjectMapper objectMapper, Clock clock,
                           @Qualifier("analysisTaskExecutor") TaskExecutor taskExecutor,
                           @Qualifier("analysisHeartbeatScheduler") TaskScheduler heartbeatScheduler,
                           AnalysisUploadStorage uploadStorage,
                           @Value("${evidence-rfp.analysis.lease-seconds:30}") long leaseSeconds,
                           @Value("${evidence-rfp.analysis.heartbeat-seconds:10}") long heartbeatSeconds,
                           @Value("${evidence-rfp.analysis.max-attempts:2}") int maxAttempts,
                           @Value("${evidence-rfp.analysis.recovery-batch-size:8}") int recoveryBatchSize) {
        this(repository, uploadValidator, rfpParser, evidenceExtractor, evidenceRetriever,
                answerGenerator, iamControlEvaluator, objectMapper, clock, taskExecutor,
                heartbeatScheduler, uploadStorage, Duration.ofSeconds(Math.max(5, leaseSeconds)),
                Duration.ofSeconds(Math.max(1, heartbeatSeconds)), Math.max(1, maxAttempts),
                Math.max(1, recoveryBatchSize));
    }

    private AnalysisService(AnalysisRepository repository, UploadValidator uploadValidator,
                            RfpParser rfpParser, EvidenceExtractor evidenceExtractor,
                            EvidenceRetriever evidenceRetriever, AnswerGenerator answerGenerator,
                            IamControlEvaluator iamControlEvaluator, ObjectMapper objectMapper,
                            Clock clock, TaskExecutor taskExecutor, TaskScheduler heartbeatScheduler,
                            AnalysisUploadStorage uploadStorage, Duration leaseDuration,
                            Duration heartbeatInterval, int maxAttempts, int recoveryBatchSize) {
        this.repository = repository;
        this.uploadValidator = uploadValidator;
        this.rfpParser = rfpParser;
        this.evidenceExtractor = evidenceExtractor;
        this.evidenceRetriever = evidenceRetriever;
        this.answerGenerator = answerGenerator;
        this.iamControlEvaluator = iamControlEvaluator;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.taskExecutor = taskExecutor;
        this.heartbeatScheduler = heartbeatScheduler;
        this.uploadStorage = uploadStorage;
        this.leaseDuration = leaseDuration;
        this.heartbeatInterval = heartbeatInterval;
        this.maxAttempts = maxAttempts;
        this.recoveryBatchSize = recoveryBatchSize;
    }

    public Analysis create(String customerName, MultipartFile rfp, List<MultipartFile> evidence,
                           MultipartFile iamSnapshotFile) {
        return create(customerName, rfp, evidence, iamSnapshotFile, null);
    }

    public Analysis create(String customerName, MultipartFile rfp, List<MultipartFile> evidence,
                           MultipartFile iamSnapshotFile, String idempotencyKey) {
        uploadValidator.validate(rfp, evidence, iamSnapshotFile);
        String normalizedCustomerName = normalizedCustomerName(customerName);
        String normalizedKey = normalizedIdempotencyKey(idempotencyKey);
        UUID analysisId = UUID.randomUUID();
        List<AnalysisUpload> uploads = uploadStorage.store(analysisId, rfp, evidence, iamSnapshotFile);
        Instant createdAt = clock.instant();
        Analysis accepted = new Analysis(analysisId, normalizedCustomerName, false,
                AnalysisStatus.PROCESSING, createdAt, null, List.of(), List.of(), List.of());
        AnalysisJob job = AnalysisJob.queued(analysisId, createdAt, maxAttempts);
        try {
            AnalysisSubmission submission = repository.enqueue(accepted, job, uploads, normalizedKey,
                    uploadStorage.requestHash(normalizedCustomerName, uploads));
            if (!submission.created()) {
                safeDeleteUploads(analysisId);
                return submission.analysis();
            }
            dispatch(analysisId);
            return accepted;
        } catch (RuntimeException exception) {
            safeDeleteUploads(analysisId);
            throw exception;
        }
    }

    @Scheduled(fixedDelayString = "${evidence-rfp.analysis.recovery-interval-ms:5000}",
            initialDelayString = "${evidence-rfp.analysis.recovery-initial-delay-ms:1000}")
    public void recoverRunnableJobs() {
        Instant now = clock.instant();
        repository.failExhaustedJobs(now);
        processCleanupTasks(now);
        sweepOrphanedUploadDirectories(now);
        for (UUID id : repository.findRunnableJobIds(now, recoveryBatchSize)) dispatch(id);
    }

    private void dispatch(UUID analysisId) {
        try {
            taskExecutor.execute(() -> executeClaimedJob(analysisId));
        } catch (TaskRejectedException exception) {
            LOGGER.info("Analysejob {} bleibt wegen ausgelasteter lokaler Queue persistent eingereiht", analysisId);
        }
    }

    private void executeClaimedJob(UUID analysisId) {
        String leaseOwner = workerInstanceId + ":" + UUID.randomUUID();
        Instant now = clock.instant();
        if (repository.claimJob(analysisId, leaseOwner, now, now.plus(leaseDuration)).isEmpty()) return;

        AtomicBoolean leaseLost = new AtomicBoolean(false);
        ScheduledFuture<?> heartbeat = scheduleHeartbeat(analysisId, leaseOwner, leaseLost);
        boolean terminalTransition = false;
        boolean analysisDeleted = false;
        try {
            Analysis accepted = repository.findById(analysisId)
                    .orElseThrow(JobOwnershipLostException::new);
            List<AnalysisUpload> staged = repository.findUploads(analysisId);
            MultipartFile rfp = staged.stream()
                    .filter(upload -> upload.kind() == AnalysisUploadKind.RFP)
                    .findFirst().map(uploadStorage::open)
                    .orElseThrow(() -> new IllegalStateException("Persistierter RFP-Upload fehlt."));
            List<MultipartFile> evidence = staged.stream()
                    .filter(upload -> upload.kind() == AnalysisUploadKind.EVIDENCE)
                    .sorted(Comparator.comparingInt(AnalysisUpload::position))
                    .map(uploadStorage::open)
                    .toList();
            MultipartFile iamSnapshot = staged.stream()
                    .filter(upload -> upload.kind() == AnalysisUploadKind.IAM_SNAPSHOT)
                    .findFirst().map(uploadStorage::open).orElse(null);

            Analysis completed = process(accepted, rfp, evidence, iamSnapshot, leaseOwner, leaseLost);
            terminalTransition = repository.completeJob(completed, leaseOwner, completed.completedAt());
            if (!terminalTransition) throw new JobOwnershipLostException();
        } catch (JobOwnershipLostException exception) {
            analysisDeleted = analysisMissing(analysisId);
            LOGGER.info("Analysejob {} wurde beendet, weil Lease oder Analyse nicht mehr existiert", analysisId);
        } catch (RuntimeException exception) {
            String failureCode = safeFailureCode(exception);
            LOGGER.atError().addKeyValue("analysisId", analysisId)
                    .addKeyValue("phase", "PROCESSING").addKeyValue("failureCode", failureCode)
                    .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                    .log("Analyse ist kontrolliert fehlgeschlagen");
            terminalTransition = repository.failJob(analysisId, leaseOwner, failureCode, clock.instant());
            if (!terminalTransition) analysisDeleted = analysisMissing(analysisId);
        } finally {
            if (heartbeat != null) heartbeat.cancel(false);
            if (terminalTransition || analysisDeleted) {
                processCleanupTasks(clock.instant());
            }
        }
    }

    private Analysis process(Analysis accepted, MultipartFile rfp, List<MultipartFile> evidence,
                             MultipartFile iamSnapshotFile, String leaseOwner,
                             AtomicBoolean leaseLost) {
        List<RfpQuestion> inputQuestions = rfpParser.parse(rfp);
        List<ExtractedEvidence> extracted = evidence.stream().map(evidenceExtractor::extract).toList();
        advance(accepted.id(), leaseOwner, AnalysisPhase.INDEXING, 35, leaseLost);

        List<EvidenceChunk> chunks = extracted.stream().flatMap(item -> item.chunks().stream()).toList();
        Map<String, RetrievalResult> retrievedEvidence =
                evidenceRetriever.retrieveBatchWithProvenance(inputQuestions, chunks, 6);
        advance(accepted.id(), leaseOwner, AnalysisPhase.ANSWERING, 55, leaseLost);

        List<QuestionResult> questions = inputQuestions.stream()
                .map(question -> {
                    RetrievalResult retrieval = retrievedEvidence.getOrDefault(
                            question.id(), new RetrievalResult(question.id(), List.of()));
                    QuestionResult generated = answerGenerator.generate(question, retrieval.chunks());
                    return attachRetrievalProvenance(generated, retrieval);
                })
                .toList();
        advance(accepted.id(), leaseOwner, AnalysisPhase.IAM_CHECKS, 80, leaseLost);

        List<Finding> findings = new ArrayList<>();
        questions.forEach(question -> findings.addAll(question.findings()));
        extracted.stream().filter(item -> item.document().stale()).forEach(item -> findings.add(Finding.of(
                FindingType.STALE_DOCUMENT, FindingSeverity.WARNING, "EVIDENCE-003",
                "Veraltetes Dokument", "Das Dokument ist älter als den konfigurierten Aktualitätszeitraum.",
                item.document().name(), "Eine aktuelle, freigegebene Version anfordern.", true)));
        IamSnapshot iamSnapshot = parseIamSnapshot(iamSnapshotFile);
        findings.addAll(iamControlEvaluator.evaluate(iamSnapshot));
        advance(accepted.id(), leaseOwner, AnalysisPhase.PERSISTING, 95, leaseLost);

        Instant completedAt = clock.instant();
        return new Analysis(accepted.id(), accepted.customerName(), accepted.synthetic(),
                AnalysisStatus.COMPLETED, accepted.createdAt(), completedAt, questions, findings,
                extracted.stream().map(this::documentWithPassages).toList());
    }

    private void advance(UUID analysisId, String leaseOwner, AnalysisPhase phase, int progress,
                         AtomicBoolean leaseLost) {
        if (leaseLost.get()) throw new JobOwnershipLostException();
        Instant now = clock.instant();
        if (!repository.advanceJob(analysisId, leaseOwner, phase, progress, now, now.plus(leaseDuration))) {
            throw new JobOwnershipLostException();
        }
        LOGGER.atInfo().addKeyValue("analysisId", analysisId)
                .addKeyValue("phase", phase).addKeyValue("progress", progress)
                .log("Analysephase fortgeschrieben");
    }

    private ScheduledFuture<?> scheduleHeartbeat(UUID analysisId, String leaseOwner,
                                                  AtomicBoolean leaseLost) {
        if (heartbeatScheduler == null) return null;
        return heartbeatScheduler.scheduleAtFixedRate(() -> {
            Instant now = clock.instant();
            if (!repository.renewLease(analysisId, leaseOwner, now, now.plus(leaseDuration))) {
                leaseLost.set(true);
            }
        }, heartbeatInterval);
    }

    public Analysis get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AnalysisNotFoundException(id));
    }

    public Analysis getCompleted(UUID id) {
        Analysis analysis = get(id);
        if (analysis.status() != AnalysisStatus.COMPLETED) {
            throw new AnalysisNotReadyException(analysis.status());
        }
        return analysis;
    }

    public AnalysisJob getJob(UUID id) {
        Analysis analysis = get(id);
        return repository.findJobByAnalysisId(id).orElseGet(() -> syntheticJobFor(analysis));
    }

    public AnalysisStatusSnapshot getStatusSnapshot(UUID id) {
        return repository.findStatusSnapshotById(id)
                .orElseThrow(() -> new AnalysisNotFoundException(id));
    }

    public QuestionResult updateQuestion(UUID analysisId, String questionId, long expectedRevision,
                                         String answer, ReviewStatus reviewStatus) {
        QuestionResult updated = repository.updateQuestion(analysisId, questionId, expectedRevision,
                answer, reviewStatus, clock.instant());
        LOGGER.atInfo().addKeyValue("analysisId", analysisId)
                .addKeyValue("questionId", questionId).addKeyValue("phase", "REVIEW")
                .addKeyValue("revision", updated.revision()).log("Reviewfrage atomar aktualisiert");
        return updated;
    }

    public QuestionResult updateQuestion(UUID analysisId, String questionId, String answer,
                                         ReviewStatus reviewStatus) {
        QuestionResult current = getCompleted(analysisId).questions().stream()
                .filter(question -> question.id().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Frage wurde nicht gefunden: " + questionId));
        return updateQuestion(analysisId, questionId, current.revision(), answer, reviewStatus);
    }

    public void delete(UUID id) {
        Instant now = clock.instant();
        if (!repository.deleteById(id, now)) throw new AnalysisNotFoundException(id);
        processCleanupTasks(now);
    }

    private IamSnapshot parseIamSnapshot(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            return objectMapper.readValue(file.getInputStream(), IamSnapshot.class);
        } catch (IOException | RuntimeException exception) {
            throw new InvalidUploadException("Der IAM-Snapshot ist kein gültiges JSON-Dokument.");
        }
    }

    private EvidenceDocument documentWithPassages(ExtractedEvidence evidence) {
        EvidenceDocument document = evidence.document();
        List<EvidencePassage> passages = evidence.chunks().stream()
                .map(chunk -> new EvidencePassage(chunk.id(), chunk.locator(), chunk.content()))
                .toList();
        return new EvidenceDocument(document.name(), document.mediaType(), document.chunkCount(),
                document.documentDate(), document.stale(), passages);
    }

    private QuestionResult attachRetrievalProvenance(QuestionResult result, RetrievalResult retrieval) {
        Map<UUID, com.certaaccess.evidencerfp.evidence.RetrievalProvenance> byChunk =
                retrieval.matches().stream().collect(java.util.stream.Collectors.toMap(
                        match -> match.chunk().id(),
                        com.certaaccess.evidencerfp.evidence.RetrievedEvidence::provenance,
                        (left, right) -> left));
        var citations = result.citations().stream()
                .map(citation -> new com.certaaccess.evidencerfp.analysis.domain.Citation(
                        citation.chunkId(), citation.documentName(), citation.locator(), citation.quote(),
                        byChunk.get(citation.chunkId())))
                .toList();
        return new QuestionResult(result.id(), result.question(), result.category(), result.answer(),
                result.answerStatus(), result.reviewStatus(), citations, result.internalClarification(),
                result.findings(), result.claims(), result.revision());
    }

    private AnalysisJob syntheticJobFor(Analysis analysis) {
        AnalysisPhase phase = switch (analysis.status()) {
            case PROCESSING -> AnalysisPhase.QUEUED;
            case COMPLETED -> AnalysisPhase.COMPLETED;
            case FAILED -> AnalysisPhase.FAILED;
        };
        AnalysisJobStatus status = switch (analysis.status()) {
            case PROCESSING -> AnalysisJobStatus.QUEUED;
            case COMPLETED -> AnalysisJobStatus.COMPLETED;
            case FAILED -> AnalysisJobStatus.FAILED;
        };
        int progress = analysis.status() == AnalysisStatus.COMPLETED ? 100 : 0;
        Instant updated = analysis.completedAt() == null ? analysis.createdAt() : analysis.completedAt();
        return new AnalysisJob(analysis.id(), status, phase, progress, 0, maxAttempts,
                null, null, null, null, false, analysis.createdAt(), updated, null);
    }

    private String normalizedCustomerName(String value) {
        if (value == null || value.isBlank()) return "Lokale Analyse";
        String sanitized = value.strip();
        return sanitized.length() <= 200 ? sanitized : sanitized.substring(0, 200);
    }

    private String safeFailureCode(RuntimeException exception) {
        if (exception instanceof AnswerGenerationException generationException) {
            return generationException.code();
        }
        if (exception instanceof EvidenceProviderException providerException) {
            return providerException.code();
        }
        if (exception instanceof InvalidUploadException) return "DOCUMENT_REJECTED";
        return GENERIC_FAILURE_CODE;
    }

    private String normalizedIdempotencyKey(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > 200 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidUploadException("Idempotency-Key darf höchstens 200 sichtbare Zeichen enthalten.");
        }
        return normalized;
    }

    private void safeDeleteUploads(UUID analysisId) {
        try {
            uploadStorage.delete(analysisId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Temporäre Dateien für Analyse {} konnten nicht vollständig entfernt werden",
                    analysisId, exception);
        }
    }

    private void processCleanupTasks(Instant now) {
        String cleanupOwner = workerInstanceId + ":cleanup:" + UUID.randomUUID();
        List<UUID> claimed;
        try {
            claimed = repository.claimCleanupTasks(cleanupOwner, now, now.plus(leaseDuration),
                    recoveryBatchSize);
        } catch (RuntimeException exception) {
            LOGGER.warn("Persistente Upload-Bereinigung konnte nicht geclaimt werden", exception);
            return;
        }
        for (UUID analysisId : claimed) {
            try {
                uploadStorage.delete(analysisId);
                if (!repository.completeCleanup(analysisId, cleanupOwner, clock.instant())) {
                    LOGGER.info("Cleanup-Lease für Analyse {} wurde vor Abschluss übernommen",
                            analysisId);
                }
            } catch (RuntimeException exception) {
                Instant failedAt = clock.instant();
                try {
                    repository.retryCleanup(analysisId, cleanupOwner, failedAt,
                            failedAt.plus(CLEANUP_RETRY_DELAY), CLEANUP_FAILURE_CODE);
                } catch (RuntimeException persistenceFailure) {
                    exception.addSuppressed(persistenceFailure);
                }
                LOGGER.atWarn().addKeyValue("analysisId", analysisId)
                        .addKeyValue("phase", "CLEANUP")
                        .addKeyValue("failureCode", CLEANUP_FAILURE_CODE)
                        .log("Persistente Upload-Bereinigung wird wiederholt", exception);
            }
        }
    }

    private void sweepOrphanedUploadDirectories(Instant now) {
        try (var candidates = uploadStorage.streamStaleAnalysisDirectories(
                now.minus(ORPHAN_GRACE_PERIOD))) {
            var iterator = candidates.iterator();
            int removed = 0;
            while (iterator.hasNext() && removed < recoveryBatchSize) {
                UUID analysisId = iterator.next();
                try {
                    if (!repository.isUploadStorageTracked(analysisId)) {
                        uploadStorage.delete(analysisId);
                        removed++;
                        LOGGER.atInfo().addKeyValue("analysisId", analysisId)
                                .addKeyValue("phase", "ORPHAN_CLEANUP")
                                .log("Verwaistes Upload-Verzeichnis entfernt");
                    }
                } catch (RuntimeException exception) {
                    LOGGER.atWarn().addKeyValue("analysisId", analysisId)
                            .addKeyValue("phase", "ORPHAN_CLEANUP")
                            .log("Verwaistes Upload-Verzeichnis bleibt für den nächsten Lauf erhalten",
                                    exception);
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Lokaler Analysespeicher konnte nicht auf verwaiste Uploads geprüft werden",
                    exception);
        }
    }

    private boolean analysisMissing(UUID analysisId) {
        try {
            return repository.findById(analysisId).isEmpty();
        } catch (RuntimeException exception) {
            // Bei einem Datenbankausfall bleiben die Upload-Metadaten für einen späteren Recovery-Lauf erhalten.
            return false;
        }
    }

    private static AnalysisUploadStorage testUploadStorage() {
        return new AnalysisUploadStorage(Path.of(System.getProperty("java.io.tmpdir"),
                "evidence-rfp-tests", UUID.randomUUID().toString()).toString());
    }
}
