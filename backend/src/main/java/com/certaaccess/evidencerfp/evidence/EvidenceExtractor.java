package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.analysis.domain.EvidenceDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EvidenceExtractor {
    private static final int CHUNK_SIZE = 1_200;
    private static final int CHUNK_OVERLAP = 150;
    private static final Pattern DOCUMENT_DATE = Pattern.compile(
            "(?i)(?:stand|gültig ab|version|aktualisiert)\\s*:?\\s*(\\d{4}-\\d{2}-\\d{2}|\\d{2}\\.\\d{2}\\.\\d{4})");

    private final Tika tika = new Tika();
    private final Clock clock;
    private final int staleAfterDays;

    public EvidenceExtractor(Clock clock,
                             @Value("${evidence-rfp.documents.stale-after-days:365}") int staleAfterDays) {
        this.clock = clock;
        this.staleAfterDays = staleAfterDays;
    }

    public ExtractedEvidence extract(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) throw new InvalidUploadException("Evidenzdokument ohne Dateiname.");
        String lower = filename.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".pdf")) return extractPdf(file, filename);
            if (lower.endsWith(".docx")) return extractDocx(file, filename);
            throw new InvalidUploadException("Nicht unterstütztes Evidenzformat: " + filename);
        } catch (InvalidUploadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidUploadException("Evidenzdokument " + filename
                    + " konnte nicht verarbeitet werden: " + exception.getMessage());
        }
    }

    private ExtractedEvidence extractPdf(MultipartFile file, String filename) throws IOException {
        List<EvidenceChunk> chunks = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = clean(stripper.getText(document));
                fullText.append(text).append('\n');
                appendChunks(chunks, filename, "application/pdf", "Seite " + page, text, null);
            }
        }
        ensureText(chunks, filename);
        LocalDate date = findDate(fullText.toString());
        chunks = withDate(chunks, date);
        return result(filename, "application/pdf", chunks, date);
    }

    private ExtractedEvidence extractDocx(MultipartFile file, String filename) throws Exception {
        String text = clean(tika.parseToString(file.getInputStream()));
        LocalDate date = findDate(text);
        List<EvidenceChunk> chunks = new ArrayList<>();
        appendChunks(chunks, filename,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "Dokument", text, date);
        ensureText(chunks, filename);
        return result(filename,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", chunks, date);
    }

    private ExtractedEvidence result(String filename, String mediaType, List<EvidenceChunk> chunks,
                                     LocalDate documentDate) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        boolean stale = documentDate != null && documentDate.isBefore(today.minusDays(staleAfterDays));
        return new ExtractedEvidence(
                new EvidenceDocument(filename, mediaType, chunks.size(), documentDate, stale), chunks);
    }

    private void appendChunks(List<EvidenceChunk> target, String filename, String mediaType,
                              String locator, String text, LocalDate date) {
        if (text.isBlank()) return;
        int start = 0;
        int part = 1;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_SIZE);
            if (end < text.length()) {
                int sentenceEnd = text.lastIndexOf('.', end);
                if (sentenceEnd > start + CHUNK_SIZE / 2) end = sentenceEnd + 1;
            }
            String content = text.substring(start, end).trim();
            if (!content.isBlank()) {
                target.add(new EvidenceChunk(UUID.randomUUID(), filename, mediaType,
                        locator + (text.length() > CHUNK_SIZE ? ", Abschnitt " + part : ""), content, date));
            }
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
            part++;
        }
    }

    private List<EvidenceChunk> withDate(List<EvidenceChunk> chunks, LocalDate date) {
        return chunks.stream().map(chunk -> new EvidenceChunk(chunk.id(), chunk.documentName(),
                chunk.mediaType(), chunk.locator(), chunk.content(), date)).toList();
    }

    private LocalDate findDate(String text) {
        Matcher matcher = DOCUMENT_DATE.matcher(text);
        if (!matcher.find()) return null;
        String value = matcher.group(1);
        try {
            return value.contains(".")
                    ? LocalDate.parse(value, DateTimeFormatter.ofPattern("dd.MM.uuuu"))
                    : LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String clean(String text) {
        return text == null ? "" : text.replace('\0', ' ').replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" +", " ").replaceAll("\\n{3,}", "\\n\\n").trim();
    }

    private void ensureText(List<EvidenceChunk> chunks, String filename) {
        if (chunks.isEmpty()) {
            throw new InvalidUploadException("Dokument " + filename
                    + " enthält keinen extrahierbaren Text. OCR ist im MVP nicht enthalten.");
        }
    }
}
