package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.Finding;
import com.certaaccess.evidencerfp.analysis.domain.FindingSeverity;
import com.certaaccess.evidencerfp.analysis.domain.FindingType;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import com.certaaccess.evidencerfp.evidence.EvidenceChunk;
import com.certaaccess.evidencerfp.evidence.BufferedMultipartFile;
import com.certaaccess.evidencerfp.evidence.EvidenceExtractor;
import com.certaaccess.evidencerfp.evidence.EvidenceRetriever;
import com.certaaccess.evidencerfp.evidence.ExtractedEvidence;
import com.certaaccess.evidencerfp.evidence.InvalidUploadException;
import com.certaaccess.evidencerfp.evidence.RfpParser;
import com.certaaccess.evidencerfp.evidence.RfpQuestion;
import com.certaaccess.evidencerfp.evidence.UploadValidator;
import com.certaaccess.evidencerfp.iam.IamControlEvaluator;
import com.certaaccess.evidencerfp.iam.IamSnapshot;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalysisService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisService.class);

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

    AnalysisService(AnalysisRepository repository, UploadValidator uploadValidator, RfpParser rfpParser,
                    EvidenceExtractor evidenceExtractor, EvidenceRetriever evidenceRetriever,
                    AnswerGenerator answerGenerator,
                    IamControlEvaluator iamControlEvaluator, ObjectMapper objectMapper, Clock clock) {
        this(repository, uploadValidator, rfpParser, evidenceExtractor, evidenceRetriever,
                answerGenerator, iamControlEvaluator, objectMapper, clock, Runnable::run);
    }

    @Autowired
    public AnalysisService(AnalysisRepository repository, UploadValidator uploadValidator, RfpParser rfpParser,
                           EvidenceExtractor evidenceExtractor, EvidenceRetriever evidenceRetriever,
                           AnswerGenerator answerGenerator, IamControlEvaluator iamControlEvaluator,
                           ObjectMapper objectMapper, Clock clock,
                           @Qualifier("analysisTaskExecutor") TaskExecutor taskExecutor) {
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
    }

    public Analysis create(String customerName, MultipartFile rfp, List<MultipartFile> evidence,
                           MultipartFile iamSnapshotFile) {
        uploadValidator.validate(rfp, evidence, iamSnapshotFile);
        BufferedMultipartFile bufferedRfp = BufferedMultipartFile.copyOf(rfp);
        List<MultipartFile> bufferedEvidence = evidence.stream()
                .map(BufferedMultipartFile::copyOf).map(file -> (MultipartFile) file).toList();
        MultipartFile bufferedSnapshot = iamSnapshotFile == null || iamSnapshotFile.isEmpty()
                ? null : BufferedMultipartFile.copyOf(iamSnapshotFile);
        Instant createdAt = clock.instant();
        Analysis accepted = new Analysis(UUID.randomUUID(), normalizedCustomerName(customerName), false,
                AnalysisStatus.PROCESSING, createdAt, null, List.of(), List.of(), List.of());
        repository.save(accepted);
        try {
            taskExecutor.execute(() -> process(accepted, bufferedRfp, bufferedEvidence, bufferedSnapshot));
        } catch (TaskRejectedException exception) {
            repository.deleteById(accepted.id());
            throw new AnalysisCapacityExceededException();
        }
        return accepted;
    }

    private void process(Analysis accepted, MultipartFile rfp, List<MultipartFile> evidence,
                         MultipartFile iamSnapshotFile) {
        try {
            List<RfpQuestion> inputQuestions = rfpParser.parse(rfp);
            List<ExtractedEvidence> extracted = evidence.stream().map(evidenceExtractor::extract).toList();
            List<EvidenceChunk> chunks = extracted.stream().flatMap(item -> item.chunks().stream()).toList();
            Map<String, List<EvidenceChunk>> retrievedEvidence =
                    evidenceRetriever.retrieveBatch(inputQuestions, chunks, 6);
            List<QuestionResult> questions = inputQuestions.stream()
                    .map(question -> answerGenerator.generate(question,
                            retrievedEvidence.getOrDefault(question.id(), List.of())))
                    .toList();

            List<Finding> findings = new ArrayList<>();
            questions.forEach(question -> findings.addAll(question.findings()));
            extracted.stream().filter(item -> item.document().stale()).forEach(item -> findings.add(Finding.of(
                    FindingType.STALE_DOCUMENT, FindingSeverity.WARNING, "EVIDENCE-003",
                    "Veraltetes Dokument", "Das Dokument ist älter als den konfigurierten Aktualitätszeitraum.",
                    item.document().name(), "Eine aktuelle, freigegebene Version anfordern.", true)));
            IamSnapshot iamSnapshot = parseIamSnapshot(iamSnapshotFile);
            findings.addAll(iamControlEvaluator.evaluate(iamSnapshot));

            Analysis analysis = new Analysis(accepted.id(), accepted.customerName(), accepted.synthetic(),
                    AnalysisStatus.COMPLETED, accepted.createdAt(), clock.instant(), questions, findings,
                    extracted.stream().map(ExtractedEvidence::document).toList());
            if (repository.findById(accepted.id()).isPresent()) repository.save(analysis);
        } catch (RuntimeException exception) {
            LOGGER.error("Analyse {} ist fehlgeschlagen", accepted.id(), exception);
            Analysis failed = new Analysis(accepted.id(), accepted.customerName(), accepted.synthetic(),
                    AnalysisStatus.FAILED, accepted.createdAt(), clock.instant(),
                    List.of(), List.of(), List.of());
            if (repository.findById(accepted.id()).isPresent()) repository.save(failed);
        }
    }

    public Analysis get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AnalysisNotFoundException(id));
    }

    public QuestionResult updateQuestion(UUID analysisId, String questionId, String answer,
                                         ReviewStatus reviewStatus) {
        Analysis analysis = get(analysisId);
        QuestionResult updated = analysis.updateQuestion(questionId, answer, reviewStatus);
        repository.save(analysis);
        return updated;
    }

    public void delete(UUID id) {
        if (!repository.deleteById(id)) throw new AnalysisNotFoundException(id);
    }

    private IamSnapshot parseIamSnapshot(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            return objectMapper.readValue(file.getInputStream(), IamSnapshot.class);
        } catch (IOException | RuntimeException exception) {
            throw new InvalidUploadException("Der IAM-Snapshot ist kein gültiges JSON-Dokument: "
                    + exception.getMessage());
        }
    }

    private String normalizedCustomerName(String value) {
        if (value == null || value.isBlank()) return "Lokale Analyse";
        String sanitized = value.strip();
        return sanitized.length() <= 200 ? sanitized : sanitized.substring(0, 200);
    }
}
