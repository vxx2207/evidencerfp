package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.analysis.infrastructure.AnalysisUploadStorage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RfpParser {
    public static final int MAX_QUESTIONS = 500;
    private final DocumentProcessingProperties properties;

    public RfpParser() {
        this(DocumentProcessingProperties.secureDefaults());
    }

    @Autowired
    public RfpParser(DocumentProcessingProperties properties) {
        this.properties = properties;
        ZipSecureFile.setMaxEntrySize(properties.maxOoxmlExpandedBytes());
        ZipSecureFile.setMaxTextSize(properties.maxOoxmlExpandedBytes());
        ZipSecureFile.setMinInflateRatio(1.0 / properties.maxOoxmlCompressionRatio());
    }

    public List<RfpQuestion> parse(MultipartFile file) {
        return ParserExecution.within(properties.parserTimeout(), () -> parseWorkbook(file));
    }

    private List<RfpQuestion> parseWorkbook(MultipartFile file) {
        try (WorkbookFileSource source = openWorkbookSource(file);
             Workbook workbook = WorkbookFactory.create(source.path().toFile())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new InvalidUploadException("Die RFP-Datei enthält kein Arbeitsblatt.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                throw new InvalidUploadException("Die RFP-Datei enthält keine Kopfzeile.");
            }
            DataFormatter formatter = new DataFormatter(Locale.GERMAN);
            Map<String, Integer> columns = columns(header, formatter);
            if (!columns.containsKey("id") || !columns.containsKey("question")) {
                throw new InvalidUploadException("Die RFP-Kopfzeile benötigt die Spalten ID und Question.");
            }
            List<RfpQuestion> questions = new ArrayList<>();
            for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                String id = cell(row, columns.get("id"), formatter).trim();
                String question = cell(row, columns.get("question"), formatter).trim();
                String category = columns.containsKey("category")
                        ? cell(row, columns.get("category"), formatter).trim() : "Allgemein";
                if (id.isBlank() && question.isBlank()) continue;
                if (id.isBlank() || question.isBlank()) {
                    throw new InvalidUploadException("RFP-Zeile " + (index + 1) + " benötigt ID und Question.");
                }
                if (question.length() > properties.maxQuestionCharacters()) {
                    throw new InvalidUploadException("RFP-Zeile " + (index + 1)
                            + " überschreitet das Fragenlimit von "
                            + properties.maxQuestionCharacters() + " Zeichen.");
                }
                if (questions.stream().anyMatch(existing -> existing.id().equals(id))) {
                    throw new InvalidUploadException("Doppelte RFP-ID: " + id);
                }
                questions.add(new RfpQuestion(id, question, category.isBlank() ? "Allgemein" : category));
                if (questions.size() > MAX_QUESTIONS) {
                    throw new InvalidUploadException("Die RFP-Datei darf höchstens " + MAX_QUESTIONS + " Fragen enthalten.");
                }
            }
            if (questions.isEmpty()) {
                throw new InvalidUploadException("Die RFP-Datei enthält keine Fragen.");
            }
            return questions;
        } catch (InvalidUploadException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidUploadException("Die XLSX-Datei konnte nicht sicher verarbeitet werden.");
        }
    }

    private WorkbookFileSource openWorkbookSource(MultipartFile file) throws IOException {
        if (file instanceof AnalysisUploadStorage.PathMultipartFile storedUpload) {
            return new WorkbookFileSource(storedUpload.trustedReadPath(), false);
        }
        Path temporaryPath = Files.createTempFile("evidencerfp-rfp-", ".xlsx");
        try (InputStream input = file.getInputStream();
             OutputStream output = Files.newOutputStream(temporaryPath)) {
            input.transferTo(output);
            return new WorkbookFileSource(temporaryPath, true);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporaryPath);
            throw exception;
        }
    }

    private Map<String, Integer> columns(Row header, DataFormatter formatter) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = header.getFirstCellNum(); index < header.getLastCellNum(); index++) {
            String value = cell(header, index, formatter).trim().toLowerCase(Locale.ROOT);
            columns.put(value, index);
        }
        return columns;
    }

    private String cell(Row row, int column, DataFormatter formatter) {
        return formatter.formatCellValue(row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
    }

    private record WorkbookFileSource(Path path, boolean deleteAfterUse) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (deleteAfterUse) Files.deleteIfExists(path);
        }
    }
}
