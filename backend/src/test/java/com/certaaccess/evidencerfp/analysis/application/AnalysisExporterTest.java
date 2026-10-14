package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisExporterTest {
    @Test
    void escapesEverySpreadsheetFormulaPrefix() throws Exception {
        List<String> payloads = List.of("=1+1", "+SUM(A1:A2)", "-2+3", "@malicious");
        List<QuestionResult> questions = java.util.stream.IntStream.range(0, payloads.size())
                .mapToObj(index -> new QuestionResult("RFP-" + index, "Frage", "Test", payloads.get(index),
                        AnswerStatus.SUPPORTED, ReviewStatus.DRAFT, List.of(), null, List.of()))
                .toList();
        Instant now = Instant.parse("2026-10-14T00:00:00Z");
        Analysis analysis = new Analysis(UUID.randomUUID(), "Test", true, AnalysisStatus.COMPLETED,
                now, now, questions, List.of(), List.of());

        byte[] export = new AnalysisExporter().toXlsx(analysis);

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(export))) {
            var sheet = workbook.getSheet("Antworten");
            for (int index = 0; index < payloads.size(); index++) {
                assertThat(sheet.getRow(index + 1).getCell(3).getStringCellValue())
                        .isEqualTo("'" + payloads.get(index));
            }
        }
    }
}
