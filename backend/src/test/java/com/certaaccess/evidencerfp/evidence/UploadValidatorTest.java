package com.certaaccess.evidencerfp.evidence;

import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class UploadValidatorTest {
    private final DocumentProcessingProperties documentProperties =
            DocumentProcessingProperties.secureDefaults();
    private final UploadValidator validator = new UploadValidator(
            new UploadValidationProperties(10_000, 30_000, 5),
            new OoxmlPackageValidator(documentProperties));

    @Test
    void acceptsStructurallyValidXlsxAndPdf() {
        MockMultipartFile pdf = new MockMultipartFile("evidence", "beleg.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

        assertThatCode(() -> validator.validate(validRfp("rfp.xlsx"), List.of(pdf), null))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPdfWhoseMagicBytesDoNotMatch() {
        MockMultipartFile rfp = validRfp("rfp.xlsx");
        MockMultipartFile fakePdf = new MockMultipartFile("evidence", "beleg.pdf", "application/pdf",
                "kein pdf".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(rfp, List.of(fakePdf), null))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Dateistruktur");
    }

    @Test
    void rejectsPathTraversalInFilename() {
        MockMultipartFile rfp = new MockMultipartFile("rfp", "../rfp.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4b, 0x03, 0x04});
        MockMultipartFile pdf = new MockMultipartFile("evidence", "beleg.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> validator.validate(rfp, List.of(pdf), null))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Pfadangaben");
    }

    @Test
    void rejectsOversizedFilesBeforeParsing() {
        byte[] oversized = new byte[10_001];
        oversized[0] = 0x50;
        oversized[1] = 0x4b;
        oversized[2] = 0x03;
        oversized[3] = 0x04;
        MockMultipartFile rfp = new MockMultipartFile("rfp", "rfp.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", oversized);
        MockMultipartFile pdf = new MockMultipartFile("evidence", "beleg.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> validator.validate(rfp, List.of(pdf), null))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Dateilimit");
    }

    @Test
    void rejectsUnexpectedMimeType() {
        MockMultipartFile rfp = new MockMultipartFile("rfp", "rfp.xlsx", "text/plain",
                new byte[]{0x50, 0x4b, 0x03, 0x04});
        MockMultipartFile pdf = new MockMultipartFile("evidence", "beleg.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> validator.validate(rfp, List.of(pdf), null))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("MIME-Type");
    }

    private MockMultipartFile validRfp(String filename) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("RFP").createRow(0).createCell(0).setCellValue("ID");
            workbook.write(output);
            return new MockMultipartFile("rfp", filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
