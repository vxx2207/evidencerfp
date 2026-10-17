package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.application.AnalysisExporter;
import com.certaaccess.evidencerfp.analysis.application.AnalysisCapacityExceededException;
import com.certaaccess.evidencerfp.analysis.application.AnalysisService;
import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import com.certaaccess.evidencerfp.analysis.domain.Analysis;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
                .andExpect(jsonPath("$.iamRules.length()").value(3));

        mvc.perform(get("/api/v1/analyses/{id}/result", DemoAnalysisFactory.DEMO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synthetic").value(true))
                .andExpect(jsonPath("$.questions.length()").value(24))
                .andExpect(jsonPath("$.findings.length()").value(6))
                .andExpect(jsonPath("$.evidenceDocuments[0].passages.length()").isNumber())
                .andExpect(jsonPath("$.reviewEvents.length()").value(24))
                .andExpect(jsonPath("$.reviewEvents[0].provenance").value("GENERATED"));
    }

    @Test
    void acceptsMultipartAnalysisAndReturnsLocation() throws Exception {
        when(service.create(isNull(), any(), anyList(), isNull())).thenReturn(demo);
        MockMultipartFile rfp = new MockMultipartFile("rfp", "rfp.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});
        MockMultipartFile evidence = new MockMultipartFile("evidence", "beleg.pdf",
                "application/pdf", new byte[]{2});

        mvc.perform(multipart("/api/v1/analyses").file(rfp).file(evidence))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "http://localhost/api/v1/analyses/"
                        + DemoAnalysisFactory.DEMO_ID))
                .andExpect(jsonPath("$.progressPercent").value(100));
    }

    @Test
    void reportsABusyLocalAnalysisQueueAsServiceUnavailable() throws Exception {
        when(service.create(isNull(), any(), anyList(), isNull()))
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
        when(service.updateQuestion(any(), any(), any(), any())).thenThrow(
                new ApprovalBlockedException("Die Frage kann wegen blockierender Findings nicht freigegeben werden."));

        mvc.perform(patch("/api/v1/analyses/{id}/questions/{questionId}",
                        DemoAnalysisFactory.DEMO_ID, "RFP-018")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewStatus\":\"APPROVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_BLOCKED"));
    }

    @Test
    void doesNotMisclassifyUnexpectedIllegalStateAsApprovalConflict() throws Exception {
        when(service.updateQuestion(any(), any(), any(), any())).thenThrow(
                new IllegalStateException("interne technische Ursache"));

        mvc.perform(patch("/api/v1/analyses/{id}/questions/{questionId}",
                        DemoAnalysisFactory.DEMO_ID, "RFP-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewStatus\":\"APPROVED\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(
                        "Die Anfrage konnte wegen eines internen Fehlers nicht verarbeitet werden."));
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
}
