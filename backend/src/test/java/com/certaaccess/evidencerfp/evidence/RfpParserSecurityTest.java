package com.certaaccess.evidencerfp.evidence;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RfpParserSecurityTest {
    @Test
    void rejectsQuestionOverConfiguredCharacterBudget() {
        DocumentProcessingProperties properties = new DocumentProcessingProperties(
                365, 200, 1_000_000, 2_000, 104_857_600, 100, 2_000, 12,
                Duration.ofSeconds(20));
        RfpParser parser = new RfpParser(properties);

        assertThatThrownBy(() -> parser.parse(workbook("IAM-001", "Diese Frage ist zu lang")))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Fragenlimit von 12 Zeichen");
    }

    @Test
    void doesNotExposeParserInternalsForMalformedWorkbook() {
        RfpParser parser = new RfpParser();
        MockMultipartFile malformed = new MockMultipartFile("rfp", "rfp.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4b, 0x03, 0x04});

        assertThatThrownBy(() -> parser.parse(malformed))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage("Die XLSX-Datei konnte nicht sicher verarbeitet werden.");
    }

    private MockMultipartFile workbook(String id, String question) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("RFP");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("ID");
            header.createCell(1).setCellValue("Question");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(id);
            row.createCell(1).setCellValue(question);
            workbook.write(output);
            return new MockMultipartFile("rfp", "rfp.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
