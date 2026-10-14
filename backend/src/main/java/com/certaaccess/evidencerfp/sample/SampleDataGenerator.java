package com.certaaccess.evidencerfp.sample;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erzeugt die binären, rein synthetischen Demoartefakte aus versionierbaren Markdown-Quellen.
 * Aufruf im backend-Verzeichnis: ./mvnw exec:java
 */
public final class SampleDataGenerator {
    private static final Pattern QUESTION_ROW = Pattern.compile(
            "^\\|\\s*(RFP-\\d{3})\\s*\\|\\s*([^|]+?)\\s*\\|\\s*([^|]+?)\\s*\\|.*$");
    private static final Instant FIXED_TIMESTAMP = Instant.parse("2026-09-30T12:00:00Z");

    private SampleDataGenerator() {
    }

    public static void main(String[] args) throws Exception {
        Path source = args.length >= 1 ? Path.of(args[0]) : Path.of("../examples/source");
        Path output = args.length >= 2 ? Path.of(args[1]) : Path.of("../examples/generated");
        List<Path> generated = generate(source, output);
        generated.forEach(path -> System.out.println("Erzeugt: " + path.toAbsolutePath()));
    }

    public static List<Path> generate(Path sourceDirectory, Path outputDirectory) throws IOException {
        if (!Files.isDirectory(sourceDirectory)) {
            throw new IllegalArgumentException("Quellverzeichnis fehlt: " + sourceDirectory.toAbsolutePath());
        }
        List<Path> markdown;
        try (var files = Files.list(sourceDirectory)) {
            markdown = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        Path rfpSource = markdown.stream()
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains("rfp"))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Eine Markdown-Datei mit 'rfp' im Namen ist erforderlich."));
        List<Path> evidenceSources = markdown.stream().filter(path -> !path.equals(rfpSource)).toList();
        if (evidenceSources.size() != 5) {
            throw new IllegalArgumentException("Genau fünf Markdown-Evidenzquellen sind erforderlich; gefunden: "
                    + evidenceSources.size());
        }

        Files.createDirectories(outputDirectory);
        List<Path> generated = new ArrayList<>();
        Path rfpOutput = outputDirectory.resolve("rfp-security-questionnaire.xlsx");
        writeRfp(rfpSource, rfpOutput);
        generated.add(rfpOutput);

        for (int index = 0; index < evidenceSources.size(); index++) {
            Path source = evidenceSources.get(index);
            String markdownText = Files.readString(source, StandardCharsets.UTF_8);
            String stem = safeStem(source.getFileName().toString().replaceFirst("(?i)\\.md$", ""));
            boolean pdf = index % 2 == 0;
            Path target = outputDirectory.resolve(stem + (pdf ? ".pdf" : ".docx"));
            if (pdf) writePdf(markdownText, target); else writeDocx(markdownText, target);
            generated.add(target);
        }
        return List.copyOf(generated);
    }

    private static void writeRfp(Path source, Path target) throws IOException {
        List<Question> questions = Files.readAllLines(source, StandardCharsets.UTF_8).stream()
                .map(QUESTION_ROW::matcher).filter(Matcher::matches)
                .map(matcher -> new Question(matcher.group(1).trim(), matcher.group(2).trim(), matcher.group(3).trim()))
                .toList();
        if (questions.size() != 24) {
            throw new IllegalArgumentException("Die RFP-Quelle muss genau 24 Tabellenzeilen enthalten; gefunden: "
                    + questions.size());
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(target)) {
            var sheet = workbook.createSheet("Security Questionnaire");
            Font font = workbook.createFont();
            font.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(font);
            Row header = sheet.createRow(0);
            String[] columns = {"ID", "Question", "Category"};
            for (int index = 0; index < columns.length; index++) {
                header.createCell(index).setCellValue(columns[index]);
                header.getCell(index).setCellStyle(headerStyle);
            }
            for (int index = 0; index < questions.size(); index++) {
                Question question = questions.get(index);
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(question.id());
                row.createCell(1).setCellValue(question.text());
                row.createCell(2).setCellValue(question.category());
            }
            sheet.setColumnWidth(0, 14 * 256);
            sheet.setColumnWidth(1, 80 * 256);
            sheet.setColumnWidth(2, 24 * 256);
            sheet.createFreezePane(0, 1);
            workbook.getProperties().getCoreProperties().setCreator("EvidenceRFP – synthetische Demo");
            workbook.getProperties().getCoreProperties().setCreated(
                    java.util.Optional.of(java.util.Date.from(FIXED_TIMESTAMP)));
            workbook.write(output);
        }
    }

    private static void writePdf(String markdown, Path target) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDDocumentInformation info = new PDDocumentInformation();
            info.setAuthor("EvidenceRFP – synthetische Demo");
            info.setTitle(firstHeading(markdown));
            Calendar fixed = GregorianCalendar.from(ZonedDateTime.ofInstant(FIXED_TIMESTAMP, ZoneOffset.UTC));
            info.setCreationDate(fixed);
            info.setModificationDate(fixed);
            document.setDocumentInformation(info);
            PdfWriter writer = new PdfWriter(document);
            for (String rawLine : markdown.split("\\R")) {
                String line = plainText(rawLine);
                if (line.isBlank()) {
                    writer.blankLine();
                } else {
                    writer.write(line, rawLine.stripLeading().startsWith("#"));
                }
            }
            writer.close();
            document.save(target.toFile());
        }
    }

    private static void writeDocx(String markdown, Path target) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(target)) {
            document.getProperties().getCoreProperties().setCreator("EvidenceRFP – synthetische Demo");
            document.getProperties().getCoreProperties().setCreated(
                    java.util.Optional.of(java.util.Date.from(FIXED_TIMESTAMP)));
            for (String rawLine : markdown.split("\\R")) {
                XWPFParagraph paragraph = document.createParagraph();
                boolean heading = rawLine.stripLeading().startsWith("#");
                if (heading) paragraph.setAlignment(ParagraphAlignment.LEFT);
                XWPFRun run = paragraph.createRun();
                run.setText(plainText(rawLine));
                run.setFontFamily("Aptos");
                run.setFontSize(heading ? 16 : 10);
                run.setBold(heading);
            }
            document.write(output);
        }
    }

    private static String firstHeading(String markdown) {
        return markdown.lines().filter(line -> line.stripLeading().startsWith("#"))
                .map(SampleDataGenerator::plainText).findFirst().orElse("CertaAccess Evidenzdokument");
    }

    private static String plainText(String markdown) {
        return markdown.replaceFirst("^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("^\\s*[-*+]\\s+", "• ")
                .replace('–', '-').replace('—', '-').replace('„', '"').replace('“', '"')
                .replace('’', '\'');
    }

    private static String safeStem(String value) {
        String stem = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9äöüß_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (stem.isBlank()) throw new IllegalArgumentException("Ungültiger Evidenzdateiname: " + value);
        return stem;
    }

    private record Question(String id, String category, String text) {
    }

    private static final class PdfWriter implements AutoCloseable {
        private static final float MARGIN = 54;
        private static final float LEADING = 14;
        private final PDDocument document;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;

        private PdfWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void write(String text, boolean heading) throws IOException {
            float size = heading ? 15 : 10;
            int maxCharacters = heading ? 70 : 95;
            for (String line : wrap(text, maxCharacters)) {
                ensureSpace();
                stream.beginText();
                stream.setFont(heading ? bold : regular, size);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(toWinAnsi(line));
                stream.endText();
                y -= heading ? 20 : LEADING;
            }
        }

        private void blankLine() {
            y -= LEADING / 2;
        }

        private void ensureSpace() throws IOException {
            if (y >= MARGIN) return;
            stream.close();
            newPage();
        }

        private void newPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        @Override
        public void close() throws IOException {
            if (stream != null) stream.close();
        }

        private static List<String> wrap(String text, int width) {
            List<String> result = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split("\\s+")) {
                if (!line.isEmpty() && line.length() + word.length() + 1 > width) {
                    result.add(line.toString());
                    line.setLength(0);
                }
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
            if (!line.isEmpty()) result.add(line.toString());
            return result.isEmpty() ? List.of("") : result;
        }

        private static String toWinAnsi(String text) {
            return text.replace('•', '-').replaceAll("[^\\x20-\\x7EäöüÄÖÜßéèáàÉÈÁÀ]", "?");
        }
    }
}
