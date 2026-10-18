package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.analysis.domain.EvidenceDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EvidenceExtractor {
    private static final String PDF_MEDIA_TYPE = "application/pdf";
    private static final String DOCX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Pattern DOCUMENT_DATE = Pattern.compile(
            "(?i)(?:stand|gültig ab|version|aktualisiert)\\s*:?\\s*(\\d{4}-\\d{2}-\\d{2}|\\d{2}\\.\\d{2}\\.\\d{4})");

    private final Tika tika = new Tika();
    private final Clock clock;
    private final DocumentProcessingProperties properties;
    private final EvidenceChunker chunker;

    public EvidenceExtractor(Clock clock, int staleAfterDays) {
        this(clock, withStaleAfterDays(staleAfterDays), new EvidenceChunker());
    }

    @Autowired
    public EvidenceExtractor(Clock clock, DocumentProcessingProperties properties,
                             EvidenceChunker chunker) {
        this.clock = clock;
        this.properties = properties;
        this.chunker = chunker;
        tika.setMaxStringLength(properties.maxExtractedCharacters() + 1);
    }

    public ExtractedEvidence extract(MultipartFile file) {
        return ParserExecution.within(properties.parserTimeout(), () -> extractWithinBudget(file));
    }

    private ExtractedEvidence extractWithinBudget(MultipartFile file) {
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
            throw new InvalidUploadException("Das Evidenzdokument konnte nicht sicher verarbeitet werden.");
        }
    }

    private ExtractedEvidence extractPdf(MultipartFile file, String filename) throws IOException {
        List<EvidenceChunk> chunks = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();
        String documentHash = sha256(file);
        try (InputStream input = file.getInputStream();
             RandomAccessReadBuffer source = new RandomAccessReadBuffer(input);
             PDDocument document = Loader.loadPDF(source, IOUtils.createTempFileOnlyStreamCache())) {
            if (document.getNumberOfPages() > properties.maxPdfPages()) {
                throw new InvalidUploadException("PDF-Dokumente dürfen höchstens "
                        + properties.maxPdfPages() + " Seiten enthalten.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = clean(stripper.getText(document));
                enforceTextBudget(fullText.length(), text.length() + 1);
                fullText.append(text).append('\n');
                chunks.addAll(chunker.chunk(documentHash, filename, PDF_MEDIA_TYPE,
                        "Seite " + page, text, null));
                enforceChunkBudget(chunks.size());
            }
        }
        ensureText(chunks, filename);
        LocalDate date = findDate(fullText.toString());
        chunks = withDate(chunks, date);
        return result(filename, PDF_MEDIA_TYPE, chunks, date);
    }

    private ExtractedEvidence extractDocx(MultipartFile file, String filename) throws Exception {
        String documentHash = sha256(file);
        String extracted;
        try (InputStream input = file.getInputStream()) {
            extracted = tika.parseToString(input, new Metadata(),
                    properties.maxExtractedCharacters() + 1);
        }
        if (extracted.length() > properties.maxExtractedCharacters()) {
            throw new InvalidUploadException("Das Dokument überschreitet das Textlimit von "
                    + properties.maxExtractedCharacters() + " Zeichen.");
        }
        String text = clean(extracted);
        LocalDate date = findDate(text);
        List<EvidenceChunk> chunks = chunker.chunk(documentHash, filename, DOCX_MEDIA_TYPE,
                "Dokument", text, date);
        enforceChunkBudget(chunks.size());
        ensureText(chunks, filename);
        return result(filename, DOCX_MEDIA_TYPE, chunks, date);
    }

    private ExtractedEvidence result(String filename, String mediaType, List<EvidenceChunk> chunks,
                                     LocalDate documentDate) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        boolean stale = documentDate != null
                && documentDate.isBefore(today.minusDays(properties.staleAfterDays()));
        return new ExtractedEvidence(
                new EvidenceDocument(filename, mediaType, chunks.size(), documentDate, stale), chunks);
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

    private void enforceTextBudget(int existingCharacters, int nextCharacters) {
        if ((long) existingCharacters + nextCharacters > properties.maxExtractedCharacters()) {
            throw new InvalidUploadException("Das Dokument überschreitet das Textlimit von "
                    + properties.maxExtractedCharacters() + " Zeichen.");
        }
    }

    private void enforceChunkBudget(int chunkCount) {
        if (chunkCount > properties.maxChunksPerFile()) {
            throw new InvalidUploadException("Das Dokument überschreitet das Chunk-Limit von "
                    + properties.maxChunksPerFile() + ".");
        }
    }

    private String sha256(MultipartFile file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 ist nicht verfügbar.", exception);
        }
        try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static DocumentProcessingProperties withStaleAfterDays(int staleAfterDays) {
        DocumentProcessingProperties defaults = DocumentProcessingProperties.secureDefaults();
        return new DocumentProcessingProperties(staleAfterDays, defaults.maxPdfPages(),
                defaults.maxExtractedCharacters(), defaults.maxChunksPerFile(),
                defaults.maxOoxmlExpandedBytes(), defaults.maxOoxmlCompressionRatio(),
                defaults.maxOoxmlEntries(), defaults.maxQuestionCharacters(), defaults.parserTimeout());
    }
}
