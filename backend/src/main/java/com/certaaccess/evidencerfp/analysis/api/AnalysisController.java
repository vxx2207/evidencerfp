package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.application.AnalysisExporter;
import com.certaaccess.evidencerfp.analysis.application.AnalysisService;
import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.evidence.InvalidUploadException;
import com.certaaccess.evidencerfp.evidence.DocumentProcessingProperties;
import com.certaaccess.evidencerfp.evidence.PgVectorEvidenceRetriever;
import com.certaaccess.evidencerfp.evidence.RfpParser;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final DocumentProcessingProperties documentProperties;
    private final String aiProvider;
    private final boolean aiEnabled;
    private final String chatModel;
    private final String embeddingModel;

    @org.springframework.beans.factory.annotation.Autowired
    public AnalysisController(AnalysisService analysisService, AnalysisExporter exporter,
                              UploadValidationProperties uploadProperties,
                              DocumentProcessingProperties documentProperties,
                              IamControlEvaluator iamControlEvaluator,
                              @Value("${evidence-rfp.ai.provider:deterministic-demo}") String aiProvider,
                              @Value("${evidence-rfp.ai.enabled:false}") boolean aiEnabled,
                              @Value("${evidence-rfp.ai.chat-model:deterministic-answer-v2}") String chatModel,
                              @Value("${evidence-rfp.ai.embedding-model:none}") String embeddingModel) {
        this.analysisService = analysisService;
        this.exporter = exporter;
        this.uploadProperties = uploadProperties;
        this.documentProperties = documentProperties;
        this.iamControlEvaluator = iamControlEvaluator;
        this.aiProvider = aiProvider;
        this.aiEnabled = aiEnabled;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    public AnalysisController(AnalysisService analysisService, AnalysisExporter exporter,
                              UploadValidationProperties uploadProperties,
                              IamControlEvaluator iamControlEvaluator, String aiProvider,
                              boolean aiEnabled) {
        this(analysisService, exporter, uploadProperties,
                new DocumentProcessingProperties(365, 200, 1_000_000, 2_000,
                        104_857_600, 100, 2_000, 4_000, java.time.Duration.ofSeconds(20)),
                iamControlEvaluator, aiProvider, aiEnabled,
                aiEnabled ? "llama3.1:8b" : "deterministic-answer-v2",
                aiEnabled ? "bge-m3" : "none");
    }

    @GetMapping("/capabilities")
    @Operation(summary = "Liefert die lokal verfügbaren Analysefähigkeiten")
    public CapabilitiesResponse capabilities() {
        var limits = new CapabilitiesResponse.Limits(uploadProperties.maxFileBytes(),
                uploadProperties.maxTotalBytes(), uploadProperties.maxEvidenceFiles(),
                documentProperties.maxPdfPages(), documentProperties.maxExtractedCharacters(),
                documentProperties.maxChunksPerFile(), RfpParser.MAX_QUESTIONS,
                documentProperties.maxQuestionCharacters(), documentProperties.maxOoxmlExpandedBytes(),
                documentProperties.maxOoxmlCompressionRatio(), documentProperties.maxOoxmlEntries(),
                documentProperties.parserTimeout().toSeconds());
        String mode = !aiEnabled ? "DETERMINISTIC_DEMO"
                : cloudAiEnabled() ? "CLOUD_AI_OPT_IN" : "LOCAL_AI";
        return new CapabilitiesResponse(mode, aiProvider,
                aiEnabled, uploadProperties.maxFileBytes(), List.of(".xlsx"), List.of(".pdf", ".docx"),
                iamControlEvaluator.ruleDescriptions(), limits,
                new CapabilitiesResponse.Models(chatModel, embeddingModel),
                PgVectorEvidenceRetriever.RETRIEVAL_VERSION,
                dataBoundary(),
                cloudAiEnabled()
                        ? List.of("Upload", "Parser", "Hybrid Retrieval", "Cloud-Provider (Opt-in)",
                                "Claim-Prüfung", "Review", "XLSX")
                        : List.of("Upload", "Parser", "Hybrid Retrieval", "Claim-Prüfung", "Review", "XLSX"),
                DemoAnalysisFactory.DEMO_ID);
    }

    @PostMapping(value = "/analyses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Importiert und analysiert einen Security-Fragebogen")
    public ResponseEntity<AnalysisStatusResponse> create(
            @RequestPart("rfp") MultipartFile rfp,
            @RequestPart("evidence") List<MultipartFile> evidence,
            @RequestPart(value = "iamSnapshot", required = false) MultipartFile iamSnapshot,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Analysis analysis = analysisService.create(customerName, rfp, evidence, iamSnapshot, idempotencyKey);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(analysis.id()).toUri();
        return ResponseEntity.status(HttpStatus.ACCEPTED).location(location)
                .body(AnalysisStatusResponse.from(analysis, analysisService.getJob(analysis.id())));
    }

    @GetMapping("/analyses/{id}")
    @Operation(summary = "Liefert Status und Fortschritt einer Analyse")
    public AnalysisStatusResponse status(@PathVariable UUID id) {
        return AnalysisStatusResponse.from(analysisService.get(id), analysisService.getJob(id));
    }

    @GetMapping("/analyses/{id}/result")
    @Operation(summary = "Liefert Fragen, Antworten, Zitate und Findings")
    public AnalysisResultResponse result(@PathVariable UUID id) {
        return AnalysisResultResponse.from(analysisService.getCompleted(id), runMetadata());
    }

    @PatchMapping("/analyses/{id}/questions/{questionId}")
    @Operation(summary = "Bearbeitet oder prüft einen Antwortentwurf")
    public ResponseEntity<AnalysisResultResponse.QuestionResponse> updateQuestion(
            @PathVariable UUID id, @PathVariable String questionId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody QuestionPatchRequest request) {
        if (request.answer() == null && request.reviewStatus() == null) {
            throw new InvalidUploadException("Mindestens answer oder reviewStatus muss gesetzt sein.");
        }
        QuestionResult updated = analysisService.updateQuestion(id, questionId, parseRevision(ifMatch),
                request.answer(), request.reviewStatus());
        return ResponseEntity.ok().eTag("\"" + updated.revision() + "\"")
                .body(AnalysisResultResponse.QuestionResponse.from(updated));
    }

    @GetMapping(value = "/analyses/{id}/export.xlsx", produces =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Exportiert das geprüfte Evidence Pack als XLSX")
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        byte[] body = exporter.toXlsx(analysisService.getCompleted(id));
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

    private long parseRevision(String value) {
        if (value == null || value.isBlank()) {
            throw new com.certaaccess.evidencerfp.analysis.application.PreconditionRequiredException();
        }
        String normalized = value.strip();
        if (normalized.startsWith("W/")) normalized = normalized.substring(2).strip();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            long revision = Long.parseLong(normalized);
            if (revision < 0) throw new NumberFormatException();
            return revision;
        } catch (NumberFormatException exception) {
            throw new InvalidUploadException("If-Match enthält keine gültige Fragenrevision.");
        }
    }

    private AnalysisResultResponse.RunMetadataResponse runMetadata() {
        return new AnalysisResultResponse.RunMetadataResponse(
                PgVectorEvidenceRetriever.RETRIEVAL_VERSION, "claims-v1", embeddingModel, chatModel,
                "Chunk-Zulässigkeit, exaktes Zitat, Zahlen, Entitäten und Negationen geprüft",
                dataBoundary());
    }

    private boolean cloudAiEnabled() {
        return aiEnabled && "openai".equalsIgnoreCase(aiProvider);
    }

    private String dataBoundary() {
        return cloudAiEnabled()
                ? "Cloud-Opt-in aktiv: abgerufene Passagen werden an den konfigurierten Provider übertragen"
                : "Lokale Verarbeitung; HTTP-Bindung ausschließlich auf 127.0.0.1";
    }
}
