package com.certaaccess.evidencerfp.evidence;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RfpParser {
    private static final int MAX_QUESTIONS = 500;

    public List<RfpQuestion> parse(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
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
            throw new InvalidUploadException("Die XLSX-Datei konnte nicht verarbeitet werden: "
                    + exception.getMessage());
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
}
