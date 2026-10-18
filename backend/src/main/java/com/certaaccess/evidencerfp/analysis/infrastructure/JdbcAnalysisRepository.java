package com.certaaccess.evidencerfp.analysis.infrastructure;

import com.certaaccess.evidencerfp.analysis.application.AnalysisRepository;
import com.certaaccess.evidencerfp.analysis.application.AnalysisNotFoundException;
import com.certaaccess.evidencerfp.analysis.application.AnalysisNotReadyException;
import com.certaaccess.evidencerfp.analysis.application.AnalysisSubmission;
import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import com.certaaccess.evidencerfp.analysis.application.EditConflictException;
import com.certaaccess.evidencerfp.analysis.application.IdempotencyKeyReusedException;
import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJob;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJobStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisPhase;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisUpload;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisUploadKind;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnswerClaim;
import com.certaaccess.evidencerfp.analysis.domain.Citation;
import com.certaaccess.evidencerfp.analysis.domain.EvidenceDocument;
import com.certaaccess.evidencerfp.analysis.domain.EvidencePassage;
import com.certaaccess.evidencerfp.analysis.domain.Finding;
import com.certaaccess.evidencerfp.analysis.domain.FindingSeverity;
import com.certaaccess.evidencerfp.analysis.domain.FindingType;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import com.certaaccess.evidencerfp.analysis.domain.ReviewAction;
import com.certaaccess.evidencerfp.analysis.domain.ReviewActor;
import com.certaaccess.evidencerfp.analysis.domain.ReviewEvent;
import com.certaaccess.evidencerfp.analysis.domain.ReviewProvenance;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistiert den vollständigen Review-Stand im Production-Profil. Retrieval-Chunks werden
 * unabhängig davon kurzlebig im pgvector-Arbeitsindex gehalten.
 */
@Repository
@Profile("production")
public class JdbcAnalysisRepository implements AnalysisRepository {
    private static final TypeReference<List<Citation>> CITATIONS = new TypeReference<>() { };
    private static final TypeReference<List<Finding>> FINDINGS = new TypeReference<>() { };
    private static final TypeReference<List<AnswerClaim>> CLAIMS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final DemoAnalysisFactory demoAnalysisFactory;

    public JdbcAnalysisRepository(JdbcClient jdbc, ObjectMapper objectMapper,
                                  DemoAnalysisFactory demoAnalysisFactory) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.demoAnalysisFactory = demoAnalysisFactory;
    }

    @PostConstruct
    void seedDemo() {
        if (findById(DemoAnalysisFactory.DEMO_ID).isEmpty()) save(demoAnalysisFactory.create());
    }

    @Override
    @Transactional
    public Analysis save(Analysis analysis) {
        jdbc.sql("""
                INSERT INTO analysis (id, customer_name, status, synthetic, created_at, completed_at)
                VALUES (:id, :customerName, :status, :synthetic, :createdAt, :completedAt)
                ON CONFLICT (id) DO UPDATE SET customer_name = EXCLUDED.customer_name,
                  status = EXCLUDED.status, synthetic = EXCLUDED.synthetic,
                  created_at = EXCLUDED.created_at, completed_at = EXCLUDED.completed_at
                """)
                .param("id", analysis.id())
                .param("customerName", analysis.customerName())
                .param("status", analysis.status().name())
                .param("synthetic", analysis.synthetic())
                .param("createdAt", Timestamp.from(analysis.createdAt()))
                .param("completedAt", analysis.completedAt() == null ? null : Timestamp.from(analysis.completedAt()))
                .update();

        jdbc.sql("DELETE FROM analysis_question WHERE analysis_id = :id").param("id", analysis.id()).update();
        int questionPosition = 0;
        for (QuestionResult question : analysis.questions()) {
            jdbc.sql("""
                    INSERT INTO analysis_question
                      (analysis_id, id, question, category, answer, answer_status, review_status,
                       internal_clarification, position, citations, question_findings, claims, revision)
                    VALUES (:analysisId, :id, :question, :category, :answer, :answerStatus, :reviewStatus,
                            :clarification, :position, CAST(:citations AS jsonb), CAST(:findings AS jsonb),
                            CAST(:claims AS jsonb), :revision)
                    """)
                    .param("analysisId", analysis.id())
                    .param("id", question.id())
                    .param("question", question.question())
                    .param("category", question.category())
                    .param("answer", question.answer())
                    .param("answerStatus", question.answerStatus().name())
                    .param("reviewStatus", question.reviewStatus().name())
                    .param("clarification", question.internalClarification())
                    .param("position", questionPosition++)
                    .param("citations", json(question.citations()))
                    .param("findings", json(question.findings()))
                    .param("claims", json(question.claims()))
                    .param("revision", question.revision())
                    .update();
        }

        jdbc.sql("DELETE FROM analysis_document WHERE analysis_id = :id").param("id", analysis.id()).update();
        int documentPosition = 0;
        for (EvidenceDocument document : analysis.evidenceDocuments()) {
            int currentDocumentPosition = documentPosition++;
            jdbc.sql("""
                    INSERT INTO analysis_document
                      (analysis_id, position, name, media_type, chunk_count, document_date, stale)
                    VALUES (:analysisId, :position, :name, :mediaType, :chunkCount, :documentDate, :stale)
                    """)
                    .param("analysisId", analysis.id())
                    .param("position", currentDocumentPosition)
                    .param("name", document.name())
                    .param("mediaType", document.mediaType())
                    .param("chunkCount", document.chunkCount())
                    .param("documentDate", document.documentDate() == null ? null : Date.valueOf(document.documentDate()))
                    .param("stale", document.stale())
                    .update();
            int passagePosition = 0;
            for (EvidencePassage passage : document.passages()) {
                jdbc.sql("""
                        INSERT INTO analysis_evidence_passage
                          (analysis_id, document_position, position, chunk_id, locator, content)
                        VALUES (:analysisId, :documentPosition, :position, :chunkId, :locator, :content)
                        """)
                        .param("analysisId", analysis.id())
                        .param("documentPosition", currentDocumentPosition)
                        .param("position", passagePosition++)
                        .param("chunkId", passage.chunkId())
                        .param("locator", passage.locator())
                        .param("content", passage.content())
                        .update();
            }
        }

        jdbc.sql("DELETE FROM finding WHERE analysis_id = :id").param("id", analysis.id()).update();
        int findingPosition = 0;
        for (Finding finding : analysis.findings()) {
            jdbc.sql("""
                    INSERT INTO finding
                      (id, analysis_id, position, type, severity, rule_id, title, description,
                       affected_object, recommendation, blocks_approval)
                    VALUES (:id, :analysisId, :position, :type, :severity, :ruleId, :title, :description,
                            :affectedObject, :recommendation, :blocksApproval)
                    """)
                    .param("id", finding.id())
                    .param("analysisId", analysis.id())
                    .param("position", findingPosition++)
                    .param("type", finding.type().name())
                    .param("severity", finding.severity().name())
                    .param("ruleId", finding.ruleId())
                    .param("title", finding.title())
                    .param("description", finding.description())
                    .param("affectedObject", finding.affectedObject())
                    .param("recommendation", finding.recommendation())
                    .param("blocksApproval", finding.blocksApproval())
                    .update();
        }

        for (ReviewEvent event : analysis.reviewEvents()) {
            jdbc.sql("""
                    INSERT INTO analysis_review_event
                      (id, analysis_id, question_id, actor, action, provenance, occurred_at,
                       before_answer, after_answer, before_review_status, after_review_status)
                    VALUES (:id, :analysisId, :questionId, :actor, :action, :provenance, :occurredAt,
                            :beforeAnswer, :afterAnswer, :beforeReviewStatus, :afterReviewStatus)
                    ON CONFLICT (id) DO NOTHING
                    """)
                    .param("id", event.id())
                    .param("analysisId", analysis.id())
                    .param("questionId", event.questionId())
                    .param("actor", event.actor().name())
                    .param("action", event.action().name())
                    .param("provenance", event.provenance().name())
                    .param("occurredAt", Timestamp.from(event.occurredAt()))
                    .param("beforeAnswer", event.beforeAnswer())
                    .param("afterAnswer", event.afterAnswer())
                    .param("beforeReviewStatus", event.beforeReviewStatus() == null
                            ? null : event.beforeReviewStatus().name())
                    .param("afterReviewStatus", event.afterReviewStatus().name())
                    .update();
        }
        return analysis;
    }

    @Override
    @Transactional
    public AnalysisSubmission enqueue(Analysis analysis, AnalysisJob job, List<AnalysisUpload> uploads,
                                      String idempotencyKey, String requestHash) {
        if (idempotencyKey != null) {
            jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(:key))")
                    .param("key", idempotencyKey)
                    .query((row, rowNumber) -> rowNumber)
                    .single();
            Optional<IdempotencyRecord> existing = jdbc.sql("""
                    SELECT request_hash, analysis_id FROM analysis_idempotency
                    WHERE idempotency_key = :key
                    """)
                    .param("key", idempotencyKey)
                    .query((row, rowNumber) -> new IdempotencyRecord(
                            row.getString("request_hash"), row.getObject("analysis_id", UUID.class)))
                    .optional();
            if (existing.isPresent()) {
                if (!existing.get().requestHash().equals(requestHash)) {
                    throw new IdempotencyKeyReusedException();
                }
                return new AnalysisSubmission(findById(existing.get().analysisId())
                        .orElseThrow(() -> new AnalysisNotFoundException(existing.get().analysisId())), false);
            }
        }

        jdbc.sql("""
                INSERT INTO analysis (id, customer_name, status, synthetic, created_at, completed_at)
                VALUES (:id, :customerName, :status, :synthetic, :createdAt, NULL)
                """)
                .param("id", analysis.id())
                .param("customerName", analysis.customerName())
                .param("status", analysis.status().name())
                .param("synthetic", analysis.synthetic())
                .param("createdAt", Timestamp.from(analysis.createdAt()))
                .update();
        jdbc.sql("""
                INSERT INTO analysis_job
                  (analysis_id, status, phase, progress_percent, attempt, max_attempts,
                   cancel_requested, created_at, updated_at)
                VALUES (:analysisId, :status, :phase, :progress, :attempt, :maxAttempts,
                        FALSE, :createdAt, :updatedAt)
                """)
                .param("analysisId", job.analysisId())
                .param("status", job.status().name())
                .param("phase", job.phase().name())
                .param("progress", job.progressPercent())
                .param("attempt", job.attempt())
                .param("maxAttempts", job.maxAttempts())
                .param("createdAt", Timestamp.from(job.createdAt()))
                .param("updatedAt", Timestamp.from(job.updatedAt()))
                .update();
        for (AnalysisUpload upload : uploads) {
            jdbc.sql("""
                    INSERT INTO analysis_job_upload
                      (id, analysis_id, kind, position, original_filename, media_type,
                       size_bytes, sha256, storage_path)
                    VALUES (:id, :analysisId, :kind, :position, :filename, :mediaType,
                            :sizeBytes, :sha256, :storagePath)
                    """)
                    .param("id", upload.id())
                    .param("analysisId", upload.analysisId())
                    .param("kind", upload.kind().name())
                    .param("position", upload.position())
                    .param("filename", upload.originalFilename())
                    .param("mediaType", upload.mediaType())
                    .param("sizeBytes", upload.sizeBytes())
                    .param("sha256", upload.sha256())
                    .param("storagePath", upload.storagePath())
                    .update();
        }
        if (idempotencyKey != null) {
            jdbc.sql("""
                    INSERT INTO analysis_idempotency
                      (idempotency_key, request_hash, analysis_id, created_at)
                    VALUES (:key, :requestHash, :analysisId, :createdAt)
                    """)
                    .param("key", idempotencyKey)
                    .param("requestHash", requestHash)
                    .param("analysisId", analysis.id())
                    .param("createdAt", Timestamp.from(job.createdAt()))
                    .update();
        }
        return new AnalysisSubmission(analysis, true);
    }

    @Override
    public Optional<AnalysisJob> findJobByAnalysisId(UUID id) {
        return jdbc.sql("""
                SELECT analysis_id, status, phase, progress_percent, attempt, max_attempts,
                       lease_owner, lease_until, heartbeat_at, failure_code, cancel_requested,
                       created_at, updated_at, started_at
                FROM analysis_job WHERE analysis_id = :id
                """)
                .param("id", id)
                .query(this::mapJob)
                .optional();
    }

    @Override
    public List<AnalysisUpload> findUploads(UUID analysisId) {
        return jdbc.sql("""
                SELECT id, analysis_id, kind, position, original_filename, media_type,
                       size_bytes, sha256, storage_path
                FROM analysis_job_upload
                WHERE analysis_id = :analysisId
                ORDER BY kind, position
                """)
                .param("analysisId", analysisId)
                .query((row, rowNumber) -> new AnalysisUpload(
                        row.getObject("id", UUID.class), row.getObject("analysis_id", UUID.class),
                        AnalysisUploadKind.valueOf(row.getString("kind")), row.getInt("position"),
                        row.getString("original_filename"), row.getString("media_type"),
                        row.getLong("size_bytes"), row.getString("sha256"), row.getString("storage_path")))
                .list();
    }

    @Override
    public List<UUID> findRunnableJobIds(Instant now, int maxResults) {
        return jdbc.sql("""
                SELECT analysis_id FROM analysis_job
                WHERE cancel_requested = FALSE AND attempt < max_attempts
                  AND (status = 'QUEUED' OR (status = 'PROCESSING' AND lease_until <= :now))
                ORDER BY created_at
                LIMIT :maxResults
                """)
                .param("now", Timestamp.from(now))
                .param("maxResults", maxResults)
                .query(UUID.class)
                .list();
    }

    @Override
    @Transactional
    public List<UUID> failExhaustedJobs(Instant now) {
        List<UUID> exhausted = jdbc.sql("""
                UPDATE analysis_job SET status = 'FAILED', phase = 'FAILED',
                  lease_owner = NULL, lease_until = NULL, heartbeat_at = :now,
                  updated_at = :now, failure_code = 'JOB_RETRIES_EXHAUSTED'
                WHERE status = 'PROCESSING' AND cancel_requested = FALSE
                  AND attempt >= max_attempts AND lease_until <= :now
                RETURNING analysis_id
                """)
                .param("now", Timestamp.from(now))
                .query(UUID.class).list();
        for (UUID analysisId : exhausted) {
            jdbc.sql("""
                    UPDATE analysis SET status = 'FAILED', completed_at = :completedAt
                    WHERE id = :analysisId AND status = 'PROCESSING'
                    """)
                    .param("analysisId", analysisId)
                    .param("completedAt", Timestamp.from(now))
                    .update();
        }
        return exhausted;
    }

    @Override
    @Transactional
    public Optional<AnalysisJob> claimJob(UUID analysisId, String leaseOwner,
                                          Instant now, Instant leaseUntil) {
        return jdbc.sql("""
                WITH candidate AS (
                  SELECT analysis_id FROM analysis_job
                  WHERE analysis_id = :analysisId AND cancel_requested = FALSE
                    AND attempt < max_attempts
                    AND (status = 'QUEUED' OR (status = 'PROCESSING' AND lease_until <= :now))
                  FOR UPDATE SKIP LOCKED
                )
                UPDATE analysis_job job SET
                  status = 'PROCESSING', phase = 'PARSING', progress_percent = 5,
                  attempt = attempt + 1, lease_owner = :leaseOwner, lease_until = :leaseUntil,
                  heartbeat_at = :now, updated_at = :now, failure_code = NULL,
                  started_at = COALESCE(started_at, :now)
                FROM candidate
                WHERE job.analysis_id = candidate.analysis_id
                RETURNING job.analysis_id, job.status, job.phase, job.progress_percent,
                          job.attempt, job.max_attempts, job.lease_owner, job.lease_until,
                          job.heartbeat_at, job.failure_code, job.cancel_requested,
                          job.created_at, job.updated_at, job.started_at
                """)
                .param("analysisId", analysisId)
                .param("leaseOwner", leaseOwner)
                .param("leaseUntil", Timestamp.from(leaseUntil))
                .param("now", Timestamp.from(now))
                .query(this::mapJob)
                .optional();
    }

    @Override
    public boolean renewLease(UUID analysisId, String leaseOwner, Instant heartbeatAt, Instant leaseUntil) {
        return jdbc.sql("""
                UPDATE analysis_job SET heartbeat_at = :heartbeatAt, lease_until = :leaseUntil,
                  updated_at = :heartbeatAt
                WHERE analysis_id = :analysisId AND status = 'PROCESSING'
                  AND cancel_requested = FALSE AND lease_owner = :leaseOwner
                """)
                .param("analysisId", analysisId)
                .param("leaseOwner", leaseOwner)
                .param("heartbeatAt", Timestamp.from(heartbeatAt))
                .param("leaseUntil", Timestamp.from(leaseUntil))
                .update() == 1;
    }

    @Override
    public boolean advanceJob(UUID analysisId, String leaseOwner, AnalysisPhase phase,
                              int progressPercent, Instant now, Instant leaseUntil) {
        return jdbc.sql("""
                UPDATE analysis_job SET phase = :phase, progress_percent = :progress,
                  heartbeat_at = :now, lease_until = :leaseUntil, updated_at = :now
                WHERE analysis_id = :analysisId AND status = 'PROCESSING'
                  AND cancel_requested = FALSE AND lease_owner = :leaseOwner
                """)
                .param("analysisId", analysisId)
                .param("leaseOwner", leaseOwner)
                .param("phase", phase.name())
                .param("progress", progressPercent)
                .param("now", Timestamp.from(now))
                .param("leaseUntil", Timestamp.from(leaseUntil))
                .update() == 1;
    }

    @Override
    @Transactional
    public boolean completeJob(Analysis analysis, String leaseOwner, Instant now) {
        int claimed = jdbc.sql("""
                UPDATE analysis_job SET status = 'COMPLETED', phase = 'COMPLETED',
                  progress_percent = 100, lease_owner = NULL, lease_until = NULL,
                  heartbeat_at = :now, updated_at = :now, failure_code = NULL
                WHERE analysis_id = :analysisId AND status = 'PROCESSING'
                  AND cancel_requested = FALSE AND lease_owner = :leaseOwner AND lease_until > :now
                """)
                .param("analysisId", analysis.id())
                .param("leaseOwner", leaseOwner)
                .param("now", Timestamp.from(now))
                .update();
        if (claimed != 1) return false;
        save(analysis);
        return true;
    }

    @Override
    @Transactional
    public boolean failJob(UUID analysisId, String leaseOwner, String failureCode, Instant now) {
        int claimed = jdbc.sql("""
                UPDATE analysis_job SET status = 'FAILED', phase = 'FAILED',
                  lease_owner = NULL, lease_until = NULL, heartbeat_at = :now,
                  updated_at = :now, failure_code = :failureCode
                WHERE analysis_id = :analysisId AND status = 'PROCESSING'
                  AND cancel_requested = FALSE AND lease_owner = :leaseOwner AND lease_until > :now
                """)
                .param("analysisId", analysisId)
                .param("leaseOwner", leaseOwner)
                .param("failureCode", failureCode)
                .param("now", Timestamp.from(now))
                .update();
        if (claimed != 1) return false;
        jdbc.sql("""
                UPDATE analysis SET status = 'FAILED', completed_at = :completedAt
                WHERE id = :analysisId
                """)
                .param("analysisId", analysisId)
                .param("completedAt", Timestamp.from(now))
                .update();
        return true;
    }

    @Override
    @Transactional
    public QuestionResult updateQuestion(UUID analysisId, String questionId, long expectedRevision,
                                         String answer, ReviewStatus reviewStatus, Instant occurredAt) {
        Analysis analysis = findById(analysisId).orElseThrow(() -> new AnalysisNotFoundException(analysisId));
        if (analysis.status() != AnalysisStatus.COMPLETED) {
            throw new AnalysisNotReadyException(analysis.status());
        }
        QuestionResult current = analysis.questions().stream()
                .filter(question -> question.id().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Frage wurde nicht gefunden: " + questionId));
        if (current.revision() != expectedRevision) throw new EditConflictException();
        QuestionResult updated = analysis.updateQuestion(questionId, answer, reviewStatus, occurredAt);
        if (updated == current) return current;
        int changed = jdbc.sql("""
                UPDATE analysis_question SET answer = :answer, review_status = :reviewStatus,
                  revision = :newRevision
                WHERE analysis_id = :analysisId AND id = :questionId AND revision = :expectedRevision
                """)
                .param("answer", updated.answer())
                .param("reviewStatus", updated.reviewStatus().name())
                .param("newRevision", updated.revision())
                .param("analysisId", analysisId)
                .param("questionId", questionId)
                .param("expectedRevision", expectedRevision)
                .update();
        if (changed != 1) throw new EditConflictException();
        insertReviewEvent(analysisId, analysis.reviewEvents().getLast());
        return updated;
    }

    @Override
    public void deleteUploadMetadata(UUID analysisId) {
        jdbc.sql("DELETE FROM analysis_job_upload WHERE analysis_id = :analysisId")
                .param("analysisId", analysisId)
                .update();
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<Analysis> findById(UUID id) {
        return jdbc.sql("""
                SELECT id, customer_name, status, synthetic, created_at, completed_at
                FROM analysis WHERE id = :id
                """)
                .param("id", id)
                .query((row, rowNumber) -> new Analysis(
                        row.getObject("id", UUID.class), row.getString("customer_name"),
                        row.getBoolean("synthetic"), AnalysisStatus.valueOf(row.getString("status")),
                        row.getTimestamp("created_at").toInstant(),
                        row.getTimestamp("completed_at") == null ? null : row.getTimestamp("completed_at").toInstant(),
                        loadQuestions(id), loadFindings(id), loadDocuments(id), loadReviewEvents(id)))
                .optional();
    }

    @Override
    @Transactional
    public boolean deleteById(UUID id) {
        jdbc.sql("""
                UPDATE analysis_job SET cancel_requested = TRUE, status = 'CANCELLED',
                  phase = 'CANCELLED', lease_owner = NULL, lease_until = NULL,
                  updated_at = now()
                WHERE analysis_id = :id AND status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')
                """)
                .param("id", id)
                .update();
        return jdbc.sql("DELETE FROM analysis WHERE id = :id").param("id", id).update() > 0;
    }

    private List<QuestionResult> loadQuestions(UUID analysisId) {
        return jdbc.sql("""
                SELECT id, question, category, answer, answer_status, review_status,
                       internal_clarification, citations::text, question_findings::text,
                       claims::text, revision
                FROM analysis_question WHERE analysis_id = :analysisId ORDER BY position
                """)
                .param("analysisId", analysisId)
                .query((row, rowNumber) -> new QuestionResult(
                        row.getString("id"), row.getString("question"), row.getString("category"),
                        row.getString("answer"), AnswerStatus.valueOf(row.getString("answer_status")),
                        ReviewStatus.valueOf(row.getString("review_status")),
                        readJson(row.getString("citations"), CITATIONS), row.getString("internal_clarification"),
                        readJson(row.getString("question_findings"), FINDINGS),
                        readJson(row.getString("claims"), CLAIMS), row.getLong("revision")))
                .list();
    }

    private List<Finding> loadFindings(UUID analysisId) {
        return jdbc.sql("""
                SELECT id, type, severity, rule_id, title, description, affected_object,
                       recommendation, blocks_approval
                FROM finding WHERE analysis_id = :analysisId ORDER BY position
                """)
                .param("analysisId", analysisId)
                .query((row, rowNumber) -> new Finding(
                        row.getObject("id", UUID.class), FindingType.valueOf(row.getString("type")),
                        FindingSeverity.valueOf(row.getString("severity")), row.getString("rule_id"),
                        row.getString("title"), row.getString("description"), row.getString("affected_object"),
                        row.getString("recommendation"), row.getBoolean("blocks_approval")))
                .list();
    }

    private List<EvidenceDocument> loadDocuments(UUID analysisId) {
        return jdbc.sql("""
                SELECT position, name, media_type, chunk_count, document_date, stale
                FROM analysis_document WHERE analysis_id = :analysisId ORDER BY position
                """)
                .param("analysisId", analysisId)
                .query((row, rowNumber) -> new EvidenceDocument(
                        row.getString("name"), row.getString("media_type"), row.getInt("chunk_count"),
                        row.getObject("document_date", LocalDate.class), row.getBoolean("stale"),
                        loadPassages(analysisId, row.getInt("position"))))
                .list();
    }

    private List<EvidencePassage> loadPassages(UUID analysisId, int documentPosition) {
        return jdbc.sql("""
                SELECT chunk_id, locator, content
                FROM analysis_evidence_passage
                WHERE analysis_id = :analysisId AND document_position = :documentPosition
                ORDER BY position
                """)
                .param("analysisId", analysisId)
                .param("documentPosition", documentPosition)
                .query((row, rowNumber) -> new EvidencePassage(
                        row.getObject("chunk_id", UUID.class), row.getString("locator"),
                        row.getString("content")))
                .list();
    }

    private List<ReviewEvent> loadReviewEvents(UUID analysisId) {
        return jdbc.sql("""
                SELECT id, question_id, actor, action, provenance, occurred_at,
                       before_answer, after_answer, before_review_status, after_review_status
                FROM analysis_review_event
                WHERE analysis_id = :analysisId
                ORDER BY ordinal
                """)
                .param("analysisId", analysisId)
                .query((row, rowNumber) -> new ReviewEvent(
                        row.getObject("id", UUID.class), row.getString("question_id"),
                        ReviewActor.valueOf(row.getString("actor")),
                        ReviewAction.valueOf(row.getString("action")),
                        ReviewProvenance.valueOf(row.getString("provenance")),
                        row.getTimestamp("occurred_at").toInstant(),
                        row.getString("before_answer"), row.getString("after_answer"),
                        row.getString("before_review_status") == null ? null
                                : ReviewStatus.valueOf(row.getString("before_review_status")),
                        ReviewStatus.valueOf(row.getString("after_review_status"))))
                .list();
    }

    private AnalysisJob mapJob(java.sql.ResultSet row, int rowNumber) throws java.sql.SQLException {
        Timestamp leaseUntil = row.getTimestamp("lease_until");
        Timestamp heartbeatAt = row.getTimestamp("heartbeat_at");
        Timestamp startedAt = row.getTimestamp("started_at");
        return new AnalysisJob(row.getObject("analysis_id", UUID.class),
                AnalysisJobStatus.valueOf(row.getString("status")),
                AnalysisPhase.valueOf(row.getString("phase")), row.getInt("progress_percent"),
                row.getInt("attempt"), row.getInt("max_attempts"), row.getString("lease_owner"),
                leaseUntil == null ? null : leaseUntil.toInstant(),
                heartbeatAt == null ? null : heartbeatAt.toInstant(), row.getString("failure_code"),
                row.getBoolean("cancel_requested"), row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(), startedAt == null ? null : startedAt.toInstant());
    }

    private void insertReviewEvent(UUID analysisId, ReviewEvent event) {
        jdbc.sql("""
                INSERT INTO analysis_review_event
                  (id, analysis_id, question_id, actor, action, provenance, occurred_at,
                   before_answer, after_answer, before_review_status, after_review_status)
                VALUES (:id, :analysisId, :questionId, :actor, :action, :provenance, :occurredAt,
                        :beforeAnswer, :afterAnswer, :beforeReviewStatus, :afterReviewStatus)
                """)
                .param("id", event.id())
                .param("analysisId", analysisId)
                .param("questionId", event.questionId())
                .param("actor", event.actor().name())
                .param("action", event.action().name())
                .param("provenance", event.provenance().name())
                .param("occurredAt", Timestamp.from(event.occurredAt()))
                .param("beforeAnswer", event.beforeAnswer())
                .param("afterAnswer", event.afterAnswer())
                .param("beforeReviewStatus", event.beforeReviewStatus() == null
                        ? null : event.beforeReviewStatus().name())
                .param("afterReviewStatus", event.afterReviewStatus().name())
                .update();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Analyse konnte nicht serialisiert werden.", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Persistierte Analyse ist ungültig.", exception);
        }
    }

    private record IdempotencyRecord(String requestHash, UUID analysisId) { }
}
