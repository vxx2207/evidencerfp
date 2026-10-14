package com.certaaccess.evidencerfp.evidence;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadValidatorTest {
    private final UploadValidator validator = new UploadValidator(
            new UploadValidationProperties(10_000, 30_000, 5));

    @Test
    void rejectsPdfWhoseMagicBytesDoNotMatch() {
        MockMultipartFile rfp = new MockMultipartFile("rfp", "rfp.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4b, 0x03, 0x04});
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
}
