package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.application.AnalysisExporter;
import com.certaaccess.evidencerfp.analysis.application.AnalysisCapacityExceededException;
import com.certaaccess.evidencerfp.analysis.application.AnalysisNotReadyException;
import com.certaaccess.evidencerfp.analysis.application.AnalysisService;
import com.certaaccess.evidencerfp.analysis.application.AnalysisStatusSnapshot;
import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import com.certaaccess.evidencerfp.analysis.application.EditConflictException;
import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJob;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJobStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisPhase;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisRunMetadata;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.ApprovalBlockedException;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.Finding;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import com.certaaccess.evidencerfp.evidence.UploadValidationProperties;
import com.certaaccess.evidencerfp.iam.IamControlEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalysisControllerTest {
    private MockMvc mvc;
    private AnalysisService service;
    private Analysis demo;

    @BeforeEach
    void setUp() {
        service = mock(AnalysisService.class);
        demo = new DemoAnalysisFactory().create();
        when(service.get(DemoAnalysisFactory.DEMO_ID)).thenReturn(demo);
        when(service.getCompleted(DemoAnalysisFactory.DEMO_ID)).thenReturn(demo);
        when(service.getJob(DemoAnalysisFactory.DEMO_ID)).thenReturn(completedJob());
        when(service.getStatusSnapshot(DemoAnalysisFactory.DEMO_ID)).thenReturn(
                new AnalysisStatusSnapshot(demo, Optional.of(completedJob())));
        AnalysisController controller = new AnalysisController(service, new AnalysisExporter(),
                new UploadValidationProperties(10_485_760, 52_428_800, 20),
                new IamControlEvaluator(Clock.systemUTC()), "deterministic-demo", false);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void exposesCapabilitiesAndSyntheticDemoResult() throws Exception {
        mvc.perform(get("/api/v1/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DETERMINISTIC_DEMO"))
                .andExpect(jsonPath("$.demoAnalysisId").value(DemoAnalysisFactory.DEMO_ID.toString()))
                .andExpect(jsonPath("$.iamRules.length()").value(3))
                .andExpect(jsonPath("$.limits.maxPdfPages").value(200))
                .andExpect(jsonPath("$.limits.maxChunksPerFile").value(2000))
                .andExpect(jsonPath("$.limits.maxRfpQuestions").value(500))
                .andExpect(jsonPath("$.limits.maxOoxmlExpandedBytes").value(104857600))
                .andExpect(jsonPath("$.limits.maxOoxmlCompressionRatio").value(100.0))
                .andExpect(jsonPath("$.models.chat").value("deterministic-answer-v2"))
                .andExpect(jsonPath("$.retrievalVersion").value("lexical-deterministic-v2"))
                .andExpect(jsonPath("$.dataFlow[2]").value("Lexical Retrieval"))
                .andExpect(jsonPath("$.dataFlow.length()").value(6));

        mvc.perform(get("/api/v1/analyses/{id}/result", DemoAnalysisFactory.DEMO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synthetic").value(true))
                .andExpect(jsonPath("$.questions.length()").value(24))
                .andExpect(jsonPath("$.findings.length()").value(6))
                .andExpect(jsonPath("$.evidenceDocuments[0].passages.length()").isNumber())
                .andExpect(jsonPath("$.reviewEvents.length()").value(24))
                .andExpect(jsonPath("$.reviewEvents[0].provenance").value("GENERATED"))
                .andExpect(jsonPath("$.reviewRevision").value(0))
                .andExpect(jsonPath("$.runMetadata.retrievalVersion").value("precomputed-demo-v1"))
                .andExpect(jsonPath("$.runMetadata.embeddingModel").value("precomputed-synthetic-fixture"))
                .andExpect(jsonPath("$.runMetadata.citationValidation").isNotEmpty());
    }

    @Test
    void exposesPersistedRunMetadataInsteadOfCurrentControllerModels() throws Exception {
        Analysis historical = new Analysis(demo.id(), demo.customerName(), demo.synthetic(),
                demo.status(), demo.createdAt(), demo.completedAt(), demo.questions(), demo.findings(),
                demo.evidenceDocuments(), demo.reviewEvents(), new AnalysisRunMetadata(
                        "hybrid-v0.1", "claims-v0.9", "historical-embedding", "historical-chat"));
        when(service.getCompleted(DemoAnalysisFactory.DEMO_ID)).thenReturn(historical);

        mvc.perform(get("/api/v1/analyses/{id}/result", DemoAnalysisFactory.DEMO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runMetadata.retrievalVersion").value("hybrid-v0.1"))
                .andExpect(jsonPath("$.runMetadata.promptVersion").value("claims-v0.9"))
                .andExpect(jsonPath("$.runMetadata.embeddingModel").value("historical-embedding"))
                .andExpect(jsonPath("$.runMetadata.chatModel").value("historical-chat"));
    }

    @Test
    void acceptsMultipartAnalysisAndReturnsLocation() throws Exception {
        when(service.create(isNull(), any(), anyList(), isNull(), eq("import-123"))).thenReturn(demo);
        MockMultipartFile rfp = new MockMultipartFile("rfp", "rfp.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
        MockMultipartFile evidence = new MockMultipartFile("evidence", "beleg.pdf",
                "application/pdf", new byte[]{2});

        mvc.perform(multipart("/api/v1/analyses").file(rfp).file(evidence)
                        .header("Idempotency-Key", "import-123"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "http://localhost/api/v1/analyses/"
                        + DemoAnalysisFactory.DEMO_ID))
                .andExpect(jsonPath("$.progressPercent").value(100));
        verify(service).create(isNull(), any(), anyList(), isNull(), eq("import-123"));
        verify(service).getStatusSnapshot(DemoAnalysisFactory.DEMO_ID);
        verify(service, never()).getJob(DemoAnalysisFactory.DEMO_ID);
    }

    @Test
    void readsAnalysisAndJobAsOneStatusSnapshot() throws Exception {
        mvc.perform(get("/api/v1/analyses/{id}", DemoAnalysisFactory.DEMO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.phase").value("COMPLETED"));

        verify(service).getStatusSnapshot(DemoAnalysisFactory.DEMO_ID);
        verify(service, never()).get(DemoAnalysisFactory.DEMO_ID);
        verify(service, never()).getJob(DemoAnalysisFactory.DEMO_ID);
    }

    @Test
    void blocksResultUntilThePersistentJobIsCompleted() throws Exception {
        when(service.getCompleted(DemoAnalysisFactory.DEMO_ID))
                .thenThrow(new AnalysisNotReadyException(AnalysisStatus.PROCESSING));

        mvc.perform(get("/api/v1/analyses/{id}/result", DemoAnalysisFactory.DEMO_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_READY"));
    }

    @Test
    void reportsABusyLocalAnalysisQueueAsServiceUnavailable() throws Exception {
        when(service.create(isNull(), any(), anyList(), isNull(), isNull()))
                .thenThrow(new AnalysisCapacityExceededException());
        MockMultipartFile rfp = new MockMultipartFile("rfp", "rfp.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
        MockMultipartFile evidence = new MockMultipartFile("evidence", "beleg.pdf",
                "application/pdf", new byte[]{2});

        mvc.perform(multipart("/api/v1/analyses").file(rfp).file(evidence))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ANALYSIS_CAPACITY_EXCEEDED"));
    }

    @Test
    void blocksApprovalWhenEvidenceIsMissing() throws Exception {
        when(service.updateQuestion(any(), any(), anyLong(), any(), any())).thenThrow(
                new ApprovalBlockedException("Die Frage kann wegen blockierender Findings nicht freigegeben werden."));

        mvc.perform(patch("/api/v1/analyses/{id}/questions/{questionId}",
                        DemoAnalysisFactory.DEMO_ID, "RFP-018")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewStatus\":\"APPROVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_BLOCKED"));
    }

    @Test
    void requiresIfMatchForEveryReviewChange() throws Exception {
        mvc.perform(patch("/api/v1/analyses/{id}/questions/{questionId}",
                        DemoAnalysisFactory.DEMO_ID, "RFP-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewStatus\":\"APPROVED\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
    }

    @Test
    void returnsEditConflictForAStaleQuestionRevision() throws Exception {
        when(service.updateQuestion(any(), any(), eq(0L), any(), any()))
                .thenThrow(new EditConflictException());

        mvc.perform(patch("/api/v1/analyses/{id}/questions/{questionId}",
                        DemoAnalysisFactory.DEMO_ID, "RFP-001")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewStatus\":\"APPROVED\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("EDIT_CONFLICT"));
    }

    @Test
    void doesNotMisclassifyUnexpectedIllegalStateAsApprovalConflict() throws Exception {
        when(service.updateQuestion(any(), any(), anyLong(), any(), any())).thenThrow(
                new IllegalStateException("interne technische Ursache"));

        mvc.perform(patch("/api/v1/analyses/{id}/questions/{questionId}",
                        DemoAnalysisFactory.DEMO_ID, "RFP-001")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewStatus\":\"APPROVED\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(
                        "Die Anfrage konnte wegen eines internen Fehlers nicht verarbeitet werden."));
    }

    @Test
    void returnsSafeProblemDetailForMalformedJson() throws Exception {
        mvc.perform(patch("/api/v1/analyses/{id}/questions/{questionId}",
                        DemoAnalysisFactory.DEMO_ID, "RFP-001")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewStatus\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.detail").value(
                        "Die Anfrage ist unvollständig oder syntaktisch ungültig."));
    }

    @Test
    void exportsEvidencePackAndDeletesAnalysis() throws Exception {
        decideEveryQuestion(demo);
        mvc.perform(get("/api/v1/analyses/{id}/export.xlsx", DemoAnalysisFactory.DEMO_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().exists("Content-Disposition"));

        doNothing().when(service).delete(DemoAnalysisFactory.DEMO_ID);
        mvc.perform(delete("/api/v1/analyses/{id}", DemoAnalysisFactory.DEMO_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsExportUntilEveryQuestionHasAHumanDecision() throws Exception {
        mvc.perform(get("/api/v1/analyses/{id}/export.xlsx", DemoAnalysisFactory.DEMO_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_INCOMPLETE"))
                .andExpect(jsonPath("$.title").value("Review unvollständig"));
    }

    private void decideEveryQuestion(Analysis analysis) {
        for (QuestionResult question : analysis.questions()) {
            ReviewStatus decision = question.answerStatus() == AnswerStatus.NOT_FOUND
                    || question.findings().stream().anyMatch(Finding::blocksApproval)
                    ? ReviewStatus.CHANGES_REQUIRED
                    : ReviewStatus.APPROVED;
            analysis.updateQuestion(question.id(), null, decision);
        }
    }

    private AnalysisJob completedJob() {
        Instant completedAt = demo.completedAt();
        return new AnalysisJob(demo.id(), AnalysisJobStatus.COMPLETED, AnalysisPhase.COMPLETED,
                100, 1, 2, null, null, completedAt, null, false,
                demo.createdAt(), completedAt, demo.createdAt());
    }
}
