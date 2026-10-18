package com.certaaccess.evidencerfp.evidence;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceExtractorSecurityTest {
    @Test
    void rejectsPdfOverPageBudgetBeforeTextExtraction() {
        EvidenceExtractor extractor = extractor(limits(1, 1_000_000, 2_000));

        assertThatThrownBy(() -> extractor.extract(pdfWithPages(2)))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("höchstens 1 Seiten");
    }

    @Test
    void rejectsDocxOverExtractedTextBudget() {
        EvidenceExtractor extractor = extractor(limits(200, 32, 2_000));

        assertThatThrownBy(() -> extractor.extract(docx("A".repeat(200))))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageNotContaining("org.apache")
                .hasMessageNotContaining("Exception");
    }

    @Test
    void rejectsDocumentOverChunkBudget() {
        EvidenceExtractor extractor = extractor(limits(200, 100_000, 1));
        String text = ("wort ").repeat(600);

        assertThatThrownBy(() -> extractor.extract(docx(text)))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Chunk-Limit von 1");
    }

    @Test
    void hidesLibraryDetailsForMalformedPdf() {
        EvidenceExtractor extractor = extractor(limits(200, 1_000_000, 2_000));
        MockMultipartFile malformed = new MockMultipartFile("evidence", "broken.pdf",
                "application/pdf", "%PDF-not-valid".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> extractor.extract(malformed))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessage("Das Evidenzdokument konnte nicht sicher verarbeitet werden.");
    }

    private EvidenceExtractor extractor(DocumentProcessingProperties properties) {
        return new EvidenceExtractor(Clock.systemUTC(), properties, new EvidenceChunker());
    }

    private DocumentProcessingProperties limits(int pages, int characters, int chunks) {
        return new DocumentProcessingProperties(365, pages, characters, chunks,
                104_857_600, 100, 2_000, 4_000, Duration.ofSeconds(20));
    }

    private MockMultipartFile pdfWithPages(int pages) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 0; index < pages; index++) document.addPage(new PDPage());
            document.save(output);
            return new MockMultipartFile("evidence", "document.pdf", "application/pdf", output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private MockMultipartFile docx(String text) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return new MockMultipartFile("evidence", "document.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
