package com.certaaccess.evidencerfp.analysis.infrastructure;

import com.certaaccess.evidencerfp.analysis.application.AnalysisRepository;
import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.Citation;
import com.certaaccess.evidencerfp.analysis.domain.EvidenceDocument;
import com.certaaccess.evidencerfp.analysis.domain.Finding;
import com.certaaccess.evidencerfp.analysis.domain.FindingSeverity;
import com.certaaccess.evidencerfp.analysis.domain.FindingType;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
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
                       internal_clarification, position, citations, question_findings)
                    VALUES (:analysisId, :id, :question, :category, :answer, :answerStatus, :reviewStatus,
                            :clarification, :position, CAST(:citations AS jsonb), CAST(:findings AS jsonb))
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
                    .update();
        }

        jdbc.sql("DELETE FROM analysis_document WHERE analysis_id = :id").param("id", analysis.id()).update();
        int documentPosition = 0;
        for (EvidenceDocument document : analysis.evidenceDocuments()) {
            jdbc.sql("""
                    INSERT INTO analysis_document
                      (analysis_id, position, name, media_type, chunk_count, document_date, stale)
                    VALUES (:analysisId, :position, :name, :mediaType, :chunkCount, :documentDate, :stale)
                    """)
                    .param("analysisId", analysis.id())
                    .param("position", documentPosition++)
                    .param("name", document.name())
                    .param("mediaType", document.mediaType())
                    .param("chunkCount", document.chunkCount())
                    .param("documentDate", document.documentDate() == null ? null : Date.valueOf(document.documentDate()))
                    .param("stale", document.stale())
                    .update();
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
        return analysis;
    }

    @Override
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
                        loadQuestions(id), loadFindings(id), loadDocuments(id)))
                .optional();
    }

    @Override
    @Transactional
    public boolean deleteById(UUID id) {
        return jdbc.sql("DELETE FROM analysis WHERE id = :id").param("id", id).update() > 0;
    }

    private List<QuestionResult> loadQuestions(UUID analysisId) {
        return jdbc.sql("""
                SELECT id, question, category, answer, answer_status, review_status,
                       internal_clarification, citations::text, question_findings::text
                FROM analysis_question WHERE analysis_id = :analysisId ORDER BY position
                """)
                .param("analysisId", analysisId)
                .query((row, rowNumber) -> new QuestionResult(
                        row.getString("id"), row.getString("question"), row.getString("category"),
                        row.getString("answer"), AnswerStatus.valueOf(row.getString("answer_status")),
                        ReviewStatus.valueOf(row.getString("review_status")),
                        readJson(row.getString("citations"), CITATIONS), row.getString("internal_clarification"),
                        readJson(row.getString("question_findings"), FINDINGS)))
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
                SELECT name, media_type, chunk_count, document_date, stale
                FROM analysis_document WHERE analysis_id = :analysisId ORDER BY position
                """)
                .param("analysisId", analysisId)
                .query((row, rowNumber) -> new EvidenceDocument(
                        row.getString("name"), row.getString("media_type"), row.getInt("chunk_count"),
                        row.getObject("document_date", LocalDate.class), row.getBoolean("stale")))
                .list();
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
}
