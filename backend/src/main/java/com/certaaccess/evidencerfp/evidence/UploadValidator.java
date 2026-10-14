package com.certaaccess.evidencerfp.evidence;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class UploadValidator {
    private static final Set<String> XLSX_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/octet-stream");
    private static final Set<String> PDF_TYPES = Set.of("application/pdf", "application/octet-stream");
    private static final Set<String> DOCX_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/octet-stream");
    private static final Set<String> JSON_TYPES = Set.of("application/json", "application/octet-stream", "text/json");

    private final UploadValidationProperties properties;

    public UploadValidator(UploadValidationProperties properties) {
        this.properties = properties;
    }

    public void validate(MultipartFile rfp, List<MultipartFile> evidence, MultipartFile iamSnapshot) {
        validateFile(rfp, ".xlsx", XLSX_TYPES, Magic.ZIP, "RFP");
        if (evidence == null || evidence.isEmpty()) {
            throw new InvalidUploadException("Mindestens ein Evidenzdokument ist erforderlich.");
        }
        if (evidence.size() > properties.maxEvidenceFiles()) {
            throw new InvalidUploadException("Es sind höchstens " + properties.maxEvidenceFiles()
                    + " Evidenzdokumente erlaubt.");
        }
        for (MultipartFile file : evidence) {
            String extension = extension(file);
            switch (extension) {
                case ".pdf" -> validateFile(file, extension, PDF_TYPES, Magic.PDF, "Evidenzdokument");
                case ".docx" -> validateFile(file, extension, DOCX_TYPES, Magic.ZIP, "Evidenzdokument");
                default -> throw new InvalidUploadException("Nur PDF- und DOCX-Evidenzdokumente sind erlaubt.");
            }
        }
        if (iamSnapshot != null && !iamSnapshot.isEmpty()) {
            validateFile(iamSnapshot, ".json", JSON_TYPES, Magic.JSON, "IAM-Snapshot");
        }
        long total = rfp.getSize() + evidence.stream().mapToLong(MultipartFile::getSize).sum()
                + (iamSnapshot == null ? 0L : iamSnapshot.getSize());
        if (total > properties.maxTotalBytes()) {
            throw new InvalidUploadException("Die Upload-Gesamtgröße überschreitet das Limit von "
                    + properties.maxTotalBytes() + " Bytes.");
        }
    }

    private void validateFile(MultipartFile file, String expectedExtension, Set<String> allowedTypes,
                              Magic magic, String role) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException(role + " darf nicht leer sein.");
        }
        if (file.getSize() > properties.maxFileBytes()) {
            throw new InvalidUploadException(role + " überschreitet das Dateilimit von "
                    + properties.maxFileBytes() + " Bytes.");
        }
        String safeName = safeFilename(file);
        if (!safeName.toLowerCase(Locale.ROOT).endsWith(expectedExtension)) {
            throw new InvalidUploadException(role + " hat eine unerlaubte Dateiendung.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new InvalidUploadException(role + " hat einen unerlaubten MIME-Type: " + contentType);
        }
        try {
            if (!magic.matches(file.getBytes())) {
                throw new InvalidUploadException(role + " stimmt nicht mit der behaupteten Dateistruktur überein.");
            }
        } catch (IOException exception) {
            throw new InvalidUploadException(role + " konnte nicht gelesen werden.");
        }
    }

    private String safeFilename(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank() || original.indexOf('\0') >= 0) {
            throw new InvalidUploadException("Ein gültiger Dateiname ist erforderlich.");
        }
        String normalized = original.replace('\\', '/');
        String leaf = Path.of(normalized).getFileName().toString();
        if (!leaf.equals(normalized) || leaf.equals(".") || leaf.equals("..")) {
            throw new InvalidUploadException("Pfadangaben im Dateinamen sind nicht erlaubt.");
        }
        return leaf;
    }

    private String extension(MultipartFile file) {
        String name = safeFilename(file).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    private enum Magic {
        PDF {
            @Override boolean matches(byte[] bytes) {
                return bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
            }
        },
        ZIP {
            @Override boolean matches(byte[] bytes) {
                return bytes.length >= 4 && bytes[0] == 0x50 && bytes[1] == 0x4b
                        && (bytes[2] == 0x03 || bytes[2] == 0x05 || bytes[2] == 0x07)
                        && (bytes[3] == 0x04 || bytes[3] == 0x06 || bytes[3] == 0x08);
            }
        },
        JSON {
            @Override boolean matches(byte[] bytes) {
                String value = new String(bytes, StandardCharsets.UTF_8).stripLeading();
                return value.startsWith("{") || value.startsWith("[");
            }
        };

        abstract boolean matches(byte[] bytes);
    }
}
