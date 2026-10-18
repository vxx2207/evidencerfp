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
                        rejectExternalRelationships(xml);
                    } else {
                        expectedContentType = hasExpectedMainContentType(xml, kind);
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
                || !entries.contains(kind.mainPart) || !expectedContentType) {
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

    private void rejectExternalRelationships(byte[] xml) throws XMLStreamException {
        XMLStreamReader reader = secureXmlFactory().createXMLStreamReader(new ByteArrayInputStream(xml));
        try {
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT
                        || !reader.getLocalName().equalsIgnoreCase("Relationship")) continue;
                String relationshipType = null;
                for (int index = 0; index < reader.getAttributeCount(); index++) {
                    if (reader.getAttributeLocalName(index).equalsIgnoreCase("TargetMode")
                            && reader.getAttributeValue(index).equalsIgnoreCase("External")) {
                        throw invalid("Externe OOXML-Beziehungen sind nicht erlaubt.");
                    }
                    if (reader.getAttributeLocalName(index).equalsIgnoreCase("Type")) {
                        relationshipType = reader.getAttributeValue(index).toLowerCase(Locale.ROOT);
                    }
                }
                if (relationshipType != null && (relationshipType.endsWith("/oleobject")
                        || relationshipType.endsWith("/package")
                        || relationshipType.endsWith("/activexcontrol"))) {
                    throw invalid("Eingebettete OLE- oder ActiveX-Inhalte sind nicht erlaubt.");
                }
            }
        } finally {
            reader.close();
        }
    }

    private boolean hasExpectedMainContentType(byte[] xml, Kind kind) throws XMLStreamException {
        XMLStreamReader reader = secureXmlFactory().createXMLStreamReader(new ByteArrayInputStream(xml));
        try {
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT
                        || !reader.getLocalName().equalsIgnoreCase("Override")) continue;
                String partName = attribute(reader, "PartName");
                String contentType = attribute(reader, "ContentType");
                if (partName != null && contentType != null
                        && partName.replaceFirst("^/", "").equalsIgnoreCase(kind.mainPart)
                        && contentType.equalsIgnoreCase(kind.mainContentType)) {
                    return true;
                }
            }
            return false;
        } finally {
            reader.close();
        }
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
