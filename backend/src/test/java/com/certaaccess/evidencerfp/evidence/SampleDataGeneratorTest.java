package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.sample.SampleDataGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SampleDataGeneratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesTwentyFourQuestionWorkbookAndParseablePdfAndDocx() throws Exception {
        Path source = Files.createDirectories(temporaryDirectory.resolve("source"));
        Path output = temporaryDirectory.resolve("generated");
        StringBuilder rfp = new StringBuilder("# Synthetischer RFP\n\n| ID | Kategorie | Frage |\n|---|---|---|\n");
        for (int index = 1; index <= 24; index++) {
            rfp.append("| RFP-").append(String.format("%03d", index))
                    .append(" | Identität | Wird Kontrolle ").append(index).append(" unterstützt? |\n");
        }
        Files.writeString(source.resolve("rfp-fragen.md"), rfp, StandardCharsets.UTF_8);
        for (int index = 1; index <= 5; index++) {
            Files.writeString(source.resolve(String.format("%02d-evidenz.md", index)),
                    "# CertaAccess Testdokument " + index + "\n\nStand: 2026-09-30\n\n"
                            + "FIDO2 und Passkeys werden unterstützt. Audit-Ereignisse können exportiert werden.\n",
                    StandardCharsets.UTF_8);
        }

        List<Path> generated = SampleDataGenerator.generate(source, output);

        assertThat(generated).hasSize(6).allMatch(Files::isRegularFile);
        Path workbook = output.resolve("rfp-security-questionnaire.xlsx");
        RfpParser rfpParser = new RfpParser();
        MockMultipartFile rfpUpload = new MockMultipartFile("rfp", workbook.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Files.readAllBytes(workbook));
        assertThat(rfpParser.parse(rfpUpload)).hasSize(24);

        EvidenceExtractor extractor = new EvidenceExtractor(
                Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC), 365);
        Path pdf = generated.stream().filter(path -> path.toString().endsWith(".pdf")).findFirst().orElseThrow();
        Path docx = generated.stream().filter(path -> path.toString().endsWith(".docx")).findFirst().orElseThrow();
        ExtractedEvidence pdfEvidence = extractor.extract(upload(pdf, "application/pdf"));
        ExtractedEvidence docxEvidence = extractor.extract(upload(docx,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertThat(pdfEvidence.chunks()).isNotEmpty();
        assertThat(docxEvidence.chunks()).isNotEmpty();
        assertThat(pdfEvidence.chunks().getFirst().content()).contains("FIDO2");
        assertThat(docxEvidence.chunks().getFirst().content()).contains("Audit-Ereignisse");
    }

    private MockMultipartFile upload(Path path, String mediaType) throws Exception {
        return new MockMultipartFile("evidence", path.getFileName().toString(), mediaType,
                Files.readAllBytes(path));
    }
}
