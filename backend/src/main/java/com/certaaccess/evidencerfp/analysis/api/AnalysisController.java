package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.application.AnalysisExporter;
import com.certaaccess.evidencerfp.analysis.application.AnalysisService;
import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.evidence.InvalidUploadException;
import com.certaaccess.evidencerfp.evidence.UploadValidationProperties;
import com.certaaccess.evidencerfp.iam.IamControlEvaluator;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AnalysisService analysisService;
    private final AnalysisExporter exporter;
    private final UploadValidationProperties uploadProperties;
    private final IamControlEvaluator iamControlEvaluator;
    private final String aiProvider;
    private final boolean aiEnabled;

    public AnalysisController(AnalysisService analysisService, AnalysisExporter exporter,
                              UploadValidationProperties uploadProperties,
                              IamControlEvaluator iamControlEvaluator,
                              @Value("${evidence-rfp.ai.provider:deterministic-demo}") String aiProvider,
                              @Value("${evidence-rfp.ai.enabled:false}") boolean aiEnabled) {
        this.analysisService = analysisService;
        this.exporter = exporter;
        this.uploadProperties = uploadProperties;
        this.iamControlEvaluator = iamControlEvaluator;
        this.aiProvider = aiProvider;
        this.aiEnabled = aiEnabled;
    }

    @GetMapping("/capabilities")
    @Operation(summary = "Liefert die lokal verfügbaren Analysefähigkeiten")
    public CapabilitiesResponse capabilities() {
        return new CapabilitiesResponse(aiEnabled ? "LOCAL_AI" : "DETERMINISTIC_DEMO", aiProvider,
                aiEnabled, uploadProperties.maxFileBytes(), List.of(".xlsx"), List.of(".pdf", ".docx"),
                iamControlEvaluator.ruleDescriptions(), DemoAnalysisFactory.DEMO_ID);
    }

    @PostMapping(value = "/analyses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Importiert und analysiert einen Security-Fragebogen")
    public ResponseEntity<AnalysisStatusResponse> create(
            @RequestPart("rfp") MultipartFile rfp,
            @RequestPart("evidence") List<MultipartFile> evidence,
            @RequestPart(value = "iamSnapshot", required = false) MultipartFile iamSnapshot,
            @RequestParam(value = "customerName", required = false) String customerName) {
        Analysis analysis = analysisService.create(customerName, rfp, evidence, iamSnapshot);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(analysis.id()).toUri();
        return ResponseEntity.status(HttpStatus.ACCEPTED).location(location)
                .body(AnalysisStatusResponse.from(analysis));
    }

    @GetMapping("/analyses/{id}")
    @Operation(summary = "Liefert Status und Fortschritt einer Analyse")
    public AnalysisStatusResponse status(@PathVariable UUID id) {
        return AnalysisStatusResponse.from(analysisService.get(id));
    }

    @GetMapping("/analyses/{id}/result")
    @Operation(summary = "Liefert Fragen, Antworten, Zitate und Findings")
    public AnalysisResultResponse result(@PathVariable UUID id) {
        return AnalysisResultResponse.from(analysisService.get(id));
    }

    @PatchMapping("/analyses/{id}/questions/{questionId}")
    @Operation(summary = "Bearbeitet oder prüft einen Antwortentwurf")
    public QuestionResult updateQuestion(@PathVariable UUID id, @PathVariable String questionId,
                                         @Valid @RequestBody QuestionPatchRequest request) {
        if (request.answer() == null && request.reviewStatus() == null) {
            throw new InvalidUploadException("Mindestens answer oder reviewStatus muss gesetzt sein.");
        }
        return analysisService.updateQuestion(id, questionId, request.answer(), request.reviewStatus());
    }

    @GetMapping(value = "/analyses/{id}/export.xlsx", produces =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Exportiert das geprüfte Evidence Pack als XLSX")
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        byte[] body = exporter.toXlsx(analysisService.get(id));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("evidence-pack-" + id + ".xlsx").build());
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @DeleteMapping("/analyses/{id}")
    @Operation(summary = "Löscht eine lokale Analyse")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        analysisService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
