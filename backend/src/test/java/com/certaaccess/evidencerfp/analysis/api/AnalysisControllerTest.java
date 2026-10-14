package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.application.AnalysisExporter;
import com.certaaccess.evidencerfp.analysis.application.AnalysisService;
import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import com.certaaccess.evidencerfp.analysis.domain.Analysis;
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
                .andExpect(jsonPath("$.findings.length()").value(6));
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
    void blocksApprovalWhenEvidenceIsMissing() throws Exception {
        when(service.updateQuestion(any(), any(), any(), any())).thenThrow(
                new IllegalStateException("Die Frage kann wegen blockierender Findings nicht freigegeben werden."));

        mvc.perform(patch("/api/v1/analyses/{id}/questions/{questionId}",
                        DemoAnalysisFactory.DEMO_ID, "RFP-018")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewStatus\":\"APPROVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_BLOCKED"));
    }

    @Test
    void exportsEvidencePackAndDeletesAnalysis() throws Exception {
        mvc.perform(get("/api/v1/analyses/{id}/export.xlsx", DemoAnalysisFactory.DEMO_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().exists("Content-Disposition"));

        doNothing().when(service).delete(DemoAnalysisFactory.DEMO_ID);
        mvc.perform(delete("/api/v1/analyses/{id}", DemoAnalysisFactory.DEMO_ID))
                .andExpect(status().isNoContent());
    }
}
