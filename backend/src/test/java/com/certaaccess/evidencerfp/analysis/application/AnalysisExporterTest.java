package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewIncompleteException;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisExporterTest {
    @Test
    void escapesEverySpreadsheetFormulaPrefix() throws Exception {
        List<String> payloads = List.of("=1+1", "+SUM(A1:A2)", "-2+3", "@malicious",
                "  =HYPERLINK(\"https://example.invalid\")", "\t@SUM(A1:A2)");
        List<QuestionResult> questions = java.util.stream.IntStream.range(0, payloads.size())
                .mapToObj(index -> new QuestionResult("RFP-" + index, "Frage", "Test", payloads.get(index),
                        AnswerStatus.SUPPORTED, ReviewStatus.APPROVED, List.of(), null, List.of()))
                .toList();
        Instant now = Instant.parse("2026-10-14T00:00:00Z");
        Analysis analysis = new Analysis(UUID.randomUUID(), "Test", true, AnalysisStatus.COMPLETED,
                now, now, questions, List.of(), List.of());

        byte[] export = new AnalysisExporter().toXlsx(analysis);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(export))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Antworten");
            assertThat(workbook.getSheetName(1)).isEqualTo("IAM Findings");
            assertThat(workbook.getSheetName(2)).isEqualTo("Quellen");
            assertThat(workbook.getSheetName(3)).isEqualTo("Review");
            var sheet = workbook.getSheet("Antworten");
            for (int index = 0; index < payloads.size(); index++) {
                assertThat(sheet.getRow(index + 1).getCell(3).getStringCellValue())
                        .isEqualTo("'" + payloads.get(index));
            }
            var review = workbook.getSheet("Review");
            assertThat(review).isNotNull();
            assertThat(review.getLastRowNum()).isEqualTo(payloads.size());
            for (int index = 0; index < payloads.size(); index++) {
                assertThat(review.getRow(index + 1).getCell(6).getStringCellValue())
                        .isEqualTo("'" + payloads.get(index));
            }
        }
    }

    @Test
    void writesGeneratedAndHumanChangesToReviewSheet() throws Exception {
        Instant generatedAt = Instant.parse("2026-10-14T00:00:00Z");
        Instant editedAt = Instant.parse("2026-10-15T09:00:00Z");
        QuestionResult draft = new QuestionResult("RFP-001", "Frage", "Test", "Entwurf",
                AnswerStatus.SUPPORTED, ReviewStatus.DRAFT, List.of(), null, List.of());
        Analysis analysis = new Analysis(UUID.randomUUID(), "Test", true, AnalysisStatus.COMPLETED,
                generatedAt, generatedAt, List.of(draft), List.of(), List.of());
        analysis.updateQuestion("RFP-001", "Redaktionell geprüft", ReviewStatus.APPROVED, editedAt);

        byte[] export = new AnalysisExporter().toXlsx(analysis);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(export))) {
            var review = workbook.getSheet("Review");
            assertThat(review.getLastRowNum()).isEqualTo(2);
            assertThat(review.getRow(1).getCell(4).getStringCellValue()).isEqualTo("GENERATED");
            assertThat(review.getRow(2).getCell(2).getStringCellValue()).isEqualTo("LOCAL_REVIEWER");
            assertThat(review.getRow(2).getCell(3).getStringCellValue())
                    .isEqualTo("ANSWER_AND_REVIEW_STATUS_CHANGED");
            assertThat(review.getRow(2).getCell(4).getStringCellValue()).isEqualTo("HUMAN_EDITED");
            assertThat(review.getRow(2).getCell(5).getStringCellValue()).isEqualTo("Entwurf");
            assertThat(review.getRow(2).getCell(6).getStringCellValue()).isEqualTo("Redaktionell geprüft");
            assertThat(review.getRow(2).getCell(7).getStringCellValue()).isEqualTo("DRAFT");
            assertThat(review.getRow(2).getCell(8).getStringCellValue()).isEqualTo("APPROVED");
        }
    }

    @Test
    void rejectsExportWhileAnyQuestionIsStillDraft() {
        Instant now = Instant.parse("2026-10-14T00:00:00Z");
        QuestionResult draft = new QuestionResult("RFP-001", "Frage", "Test", "Antwort",
                AnswerStatus.SUPPORTED, ReviewStatus.DRAFT, List.of(), null, List.of());
        Analysis analysis = new Analysis(UUID.randomUUID(), "Test", true, AnalysisStatus.COMPLETED,
                now, now, List.of(draft), List.of(), List.of());

        assertThatThrownBy(() -> new AnalysisExporter().toXlsx(analysis))
                .isInstanceOf(ReviewIncompleteException.class)
                .hasMessageContaining("Noch offene Fragen: 1");
    }
}
