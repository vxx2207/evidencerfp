package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.analysis.domain.AnalysisUploadKind;
import com.certaaccess.evidencerfp.analysis.infrastructure.AnalysisUploadStorage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceExtractorSecurityTest {
    @TempDir
    Path temporaryDirectory;

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
    void abortsPdfTextExtractionAsSoonAsRawCharacterBudgetIsExceeded() {
        EvidenceExtractor extractor = extractor(limits(200, 32, 2_000));

        assertThatThrownBy(() -> extractor.extract(pdfWithText("A".repeat(200))))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Textlimit von 32 Zeichen");
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

    @Test
    void parsesPersistedPdfDirectlyFromVerifiedStoragePath() throws Exception {
        AnalysisUploadStorage storage = new AnalysisUploadStorage(temporaryDirectory.toString());
        UUID analysisId = UUID.randomUUID();
        MockMultipartFile rfp = new MockMultipartFile(
                "rfp", "fragen.xlsx", "application/octet-stream", new byte[]{1});
        var uploads = storage.store(analysisId, rfp,
                List.of(pdfWithText("Phishing-resistente MFA ist verpflichtend.")), null);
        MultipartFile persisted = storage.open(uploads.stream()
                .filter(upload -> upload.kind() == AnalysisUploadKind.EVIDENCE)
                .findFirst().orElseThrow());

        assertThat(persisted).isInstanceOf(AnalysisUploadStorage.PathMultipartFile.class);
        Path parserPath = ((AnalysisUploadStorage.PathMultipartFile) persisted).trustedReadPath();
        assertThat(parserPath).startsWith(temporaryDirectory.toRealPath());

        ExtractedEvidence extracted = extractor(limits(200, 1_000_000, 2_000)).extract(persisted);
        assertThat(extracted.chunks())
                .extracting(EvidenceChunk::content)
                .anyMatch(content -> content.contains("Phishing-resistente MFA"));
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

    private MockMultipartFile pdfWithText(String text) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return new MockMultipartFile(
                    "evidence", "document.pdf", "application/pdf", output.toByteArray());
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
