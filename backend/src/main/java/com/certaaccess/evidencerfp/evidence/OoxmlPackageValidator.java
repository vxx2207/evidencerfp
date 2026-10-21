package com.certaaccess.evidencerfp.evidence;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Validiert den OPC/OOXML-Container vor Apache POI oder Tika. Der Scanner entpackt nie mehr als
 * das konfigurierte Budget und akzeptiert keine aktiven beziehungsweise externen Bestandteile.
 */
@Component
final class OoxmlPackageValidator {
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int MAX_INSPECTED_XML_BYTES = 2 * 1024 * 1024;
    private static final String CONTENT_TYPES = "[content_types].xml";
    private static final String ROOT_RELATIONSHIPS = "_rels/.rels";
    private static final Set<String> OFFICE_DOCUMENT_RELATIONSHIP_TYPES = Set.of(
            "http://schemas.openxmlformats.org/officedocument/2006/relationships/officedocument",
            "http://purl.oclc.org/ooxml/officedocument/relationships/officedocument");

    private final DocumentProcessingProperties properties;

    OoxmlPackageValidator(DocumentProcessingProperties properties) {
        this.properties = properties;
    }

    void validate(MultipartFile file, Kind kind) {
        ParserExecution.within(properties.parserTimeout(), () -> {
            inspect(file, kind);
            return null;
        });
    }

    private void inspect(MultipartFile file, Kind kind) {
        Set<String> entries = new HashSet<>();
        boolean expectedContentType = false;
        boolean expectedRootRelationship = false;
        long expandedTotal = 0;
        int entryCount = 0;

        try (InputStream input = file.getInputStream(); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > properties.maxOoxmlEntries()) {
                    throw invalid("Das Office-Dokument enthält zu viele ZIP-Einträge.");
                }

                String name = safeEntryName(entry.getName());
                String lowerName = name.toLowerCase(Locale.ROOT);
                if (!entries.add(lowerName)) {
                    throw invalid("Das Office-Dokument enthält doppelte ZIP-Einträge.");
                }
                rejectDangerousPart(lowerName);

                boolean inspectXml = lowerName.equals(CONTENT_TYPES) || lowerName.endsWith(".rels");
                ByteArrayOutputStream inspected = inspectXml ? new ByteArrayOutputStream() : null;
                long expandedEntry = copyEntry(zip, inspected, expandedTotal);
                expandedTotal += expandedEntry;
                enforceCompressionRatio(entry, expandedEntry, file.getSize());
                if (file.getSize() > 0 && expandedTotal / (double) file.getSize()
                        > properties.maxOoxmlCompressionRatio()) {
                    throw invalid("Das Office-Dokument überschreitet das erlaubte Kompressionsverhältnis.");
                }

                if (inspected != null) {
                    byte[] xml = inspected.toByteArray();
                    if (lowerName.endsWith(".rels")) {
                        boolean rootRelationships = lowerName.equals(ROOT_RELATIONSHIPS);
                        boolean validRoot = inspectRelationships(xml, rootRelationships, kind);
                        if (rootRelationships) {
                            expectedRootRelationship = validRoot;
                        }
                    } else {
                        expectedContentType = inspectContentTypes(xml, kind);
                    }
                }
                zip.closeEntry();
            }
        } catch (InvalidUploadException exception) {
            throw exception;
        } catch (ZipException exception) {
            throw invalid("Das Office-Dokument ist verschlüsselt oder kein gültiger OOXML-Container.");
        } catch (IOException | XMLStreamException exception) {
            throw invalid("Das Office-Dokument konnte nicht sicher geprüft werden.");
        }

        if (!entries.contains(CONTENT_TYPES) || !entries.contains(ROOT_RELATIONSHIPS)
                || !entries.contains(kind.mainPart) || !expectedContentType
                || !expectedRootRelationship) {
            throw invalid("Das Office-Dokument enthält nicht die erwartete OOXML-Struktur.");
        }
    }

    private long copyEntry(ZipInputStream zip, ByteArrayOutputStream inspected, long expandedSoFar)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long expandedEntry = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            expandedEntry += read;
            if (expandedSoFar + expandedEntry > properties.maxOoxmlExpandedBytes()) {
                throw invalid("Das entpackte Office-Dokument überschreitet das Größenlimit.");
            }
            if (inspected != null) {
                if (inspected.size() + read > MAX_INSPECTED_XML_BYTES) {
                    throw invalid("Ein OOXML-Steuerdokument überschreitet das Größenlimit.");
                }
                inspected.write(buffer, 0, read);
            }
        }
        return expandedEntry;
    }

    private void enforceCompressionRatio(ZipEntry entry, long expandedEntry, long archiveBytes) {
        long compressed = entry.getCompressedSize();
        if (expandedEntry == 0) return;
        if (compressed > 0 && expandedEntry / (double) compressed > properties.maxOoxmlCompressionRatio()) {
            throw invalid("Das Office-Dokument überschreitet das erlaubte Kompressionsverhältnis.");
        }
        if (compressed <= 0 && archiveBytes > 0
                && expandedEntry / (double) archiveBytes > properties.maxOoxmlCompressionRatio()) {
            throw invalid("Das Office-Dokument überschreitet das erlaubte Kompressionsverhältnis.");
        }
    }

    private String safeEntryName(String value) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) {
            throw invalid("Das Office-Dokument enthält einen ungültigen ZIP-Pfad.");
        }
        String normalized = value.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("(?i)^[a-z]:.*")) {
            throw invalid("Das Office-Dokument enthält einen absoluten ZIP-Pfad.");
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.equals("..") || segment.equals(".")) {
                throw invalid("Das Office-Dokument enthält ZIP-Pfad-Traversal.");
            }
        }
        return normalized;
    }

    private void rejectDangerousPart(String lowerName) {
        if (lowerName.endsWith("vbaproject.bin") || lowerName.contains("/macrosheets/")) {
            throw invalid("Office-Dokumente mit Makros sind nicht erlaubt.");
        }
        if (lowerName.contains("/embeddings/") || lowerName.contains("/oleobjects/")
                || lowerName.contains("/activex/")) {
            throw invalid("Eingebettete OLE- oder ActiveX-Inhalte sind nicht erlaubt.");
        }
        if (lowerName.endsWith("encryptedpackage") || lowerName.endsWith("encryptioninfo")) {
            throw invalid("Verschlüsselte Office-Dokumente sind nicht erlaubt.");
        }
    }

    private boolean inspectRelationships(byte[] xml, boolean rootRelationships, Kind kind)
            throws XMLStreamException {
        int officeDocumentRelationships = 0;
        boolean expectedRootTarget = false;
        XMLStreamReader reader = secureXmlFactory().createXMLStreamReader(new ByteArrayInputStream(xml));
        try {
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT
                        || !reader.getLocalName().equalsIgnoreCase("Relationship")) continue;
                String targetMode = normalizedAttribute(reader, "TargetMode");
                if ("external".equals(targetMode)) {
                    throw invalid("Externe OOXML-Beziehungen sind nicht erlaubt.");
                }
                if (targetMode != null && !"internal".equals(targetMode)) {
                    throw invalid("Das Office-Dokument enthält einen ungültigen Beziehungsmodus.");
                }

                String relationshipType = normalizedAttribute(reader, "Type");
                if (isMacroType(relationshipType)) {
                    throw invalid("Office-Dokumente mit Makros sind nicht erlaubt.");
                }
                if (relationshipType != null && (relationshipType.endsWith("/oleobject")
                        || relationshipType.endsWith("/package")
                        || relationshipType.endsWith("/activexcontrol"))) {
                    throw invalid("Eingebettete OLE- oder ActiveX-Inhalte sind nicht erlaubt.");
                }

                if (!rootRelationships || !OFFICE_DOCUMENT_RELATIONSHIP_TYPES.contains(relationshipType)) {
                    continue;
                }
                officeDocumentRelationships++;
                boolean internal = targetMode == null || targetMode.equals("internal");
                String target = attribute(reader, "Target");
                if (internal && isExpectedRootTarget(target, kind)) {
                    expectedRootTarget = true;
                }
            }
        } finally {
            reader.close();
        }

        if (!rootRelationships) {
            return false;
        }
        if (officeDocumentRelationships != 1 || !expectedRootTarget) {
            throw invalid("Das Office-Dokument besitzt keine eindeutige interne Root-Beziehung "
                    + "zum erwarteten Hauptdokument.");
        }
        return true;
    }

    private boolean inspectContentTypes(byte[] xml, Kind kind) throws XMLStreamException {
        boolean expectedMainContentType = false;
        XMLStreamReader reader = secureXmlFactory().createXMLStreamReader(new ByteArrayInputStream(xml));
        try {
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) continue;
                boolean override = reader.getLocalName().equalsIgnoreCase("Override");
                boolean defaultType = reader.getLocalName().equalsIgnoreCase("Default");
                if (!override && !defaultType) continue;

                String partName = attribute(reader, "PartName");
                String contentType = attribute(reader, "ContentType");
                if (isMacroType(contentType)) {
                    throw invalid("Office-Dokumente mit Makros sind nicht erlaubt.");
                }
                if (override && partName != null && contentType != null
                        && partName.replaceFirst("^/", "").equalsIgnoreCase(kind.mainPart)
                        && contentType.equalsIgnoreCase(kind.mainContentType)) {
                    expectedMainContentType = true;
                }
            }
            return expectedMainContentType;
        } finally {
            reader.close();
        }
    }

    private boolean isMacroType(String value) {
        if (value == null) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("macro") || normalized.contains("vba");
    }

    private boolean isExpectedRootTarget(String target, Kind kind) {
        if (target == null || target.isBlank() || target.indexOf('\\') >= 0
                || target.indexOf('?') >= 0 || target.indexOf('#') >= 0) {
            return false;
        }
        String normalized = target.startsWith("/") ? target.substring(1) : target;
        return normalized.equals(kind.mainPart);
    }

    private String normalizedAttribute(XMLStreamReader reader, String name) {
        String value = attribute(reader, name);
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    private String attribute(XMLStreamReader reader, String name) {
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            if (reader.getAttributeLocalName(index).equalsIgnoreCase(name)) {
                return reader.getAttributeValue(index);
            }
        }
        return null;
    }

    private XMLInputFactory secureXmlFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        return factory;
    }

    private InvalidUploadException invalid(String message) {
        return new InvalidUploadException(message);
    }

    enum Kind {
        DOCX("word/document.xml",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"),
        XLSX("xl/workbook.xml",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml");

        private final String mainPart;
        private final String mainContentType;

        Kind(String mainPart, String mainContentType) {
            this.mainPart = mainPart;
            this.mainContentType = mainContentType;
        }
    }
}
