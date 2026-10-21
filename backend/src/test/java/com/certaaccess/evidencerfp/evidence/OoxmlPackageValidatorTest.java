package com.certaaccess.evidencerfp.evidence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OoxmlPackageValidatorTest {
    private static final String DOCX_CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Override PartName="/word/document.xml"
                ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
            """;
    private static final String XLSX_CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Override PartName="/xl/workbook.xml"
                ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
            </Types>
            """;
    private static final String DOCX_RELATIONSHIPS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="r1"
                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                Target="word/document.xml"/>
            </Relationships>
            """;
    private static final String XLSX_RELATIONSHIPS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="r1"
                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                Target="xl/workbook.xml" TargetMode="Internal"/>
            </Relationships>
            """;

    @Test
    void acceptsMinimalDocxStructure() {
        validator(defaults()).validate(docx(Map.of()), OoxmlPackageValidator.Kind.DOCX);
    }

    @Test
    void acceptsMinimalXlsxStructureWithExplicitInternalRootRelationship() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", XLSX_CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
        entries.put("_rels/.rels", XLSX_RELATIONSHIPS.getBytes(StandardCharsets.UTF_8));
        entries.put("xl/workbook.xml", "<workbook/>".getBytes(StandardCharsets.UTF_8));

        validator(defaults()).validate(zip("questions.xlsx", entries), OoxmlPackageValidator.Kind.XLSX);
    }

    @Test
    void rejectsEmptyWrongAndDuplicateRootOfficeDocumentRelationships() {
        Map<String, byte[]> emptyRoot = baseEntries();
        emptyRoot.put("_rels/.rels", """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
                """.getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> wrongTarget = baseEntries();
        wrongTarget.put("_rels/.rels", rootRelationships("word/other.xml", ""));
        Map<String, byte[]> wrongType = baseEntries();
        wrongType.put("_rels/.rels", """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="r1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"
                    Target="word/document.xml"/>
                </Relationships>
                """.getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> duplicate = baseEntries();
        duplicate.put("_rels/.rels", rootRelationships("word/document.xml", """
                <Relationship Id="r2"
                  Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                  Target="word/document.xml"/>
                """));

        assertThatThrownBy(() -> validator(defaults()).validate(zip("empty-root.docx", emptyRoot),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Root-Beziehung");
        assertThatThrownBy(() -> validator(defaults()).validate(zip("wrong-root.docx", wrongTarget),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Root-Beziehung");
        assertThatThrownBy(() -> validator(defaults()).validate(zip("wrong-type.docx", wrongType),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Root-Beziehung");
        assertThatThrownBy(() -> validator(defaults()).validate(zip("duplicate-root.docx", duplicate),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Root-Beziehung");
    }

    @Test
    void rejectsMissingRequiredMainPart() {
        Map<String, byte[]> entries = baseEntries();
        entries.remove("word/document.xml");

        assertThatThrownBy(() -> validator(defaults()).validate(zip("bad.docx", entries),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("OOXML-Struktur");
    }

    @Test
    void rejectsExternalRelationship() {
        Map<String, byte[]> entries = baseEntries();
        entries.put("word/_rels/document.xml.rels", """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="r1" Target="https://attacker.invalid/payload" TargetMode="External"/>
                </Relationships>
                """.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator(defaults()).validate(zip("external.docx", entries),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Externe OOXML-Beziehungen");
    }

    @Test
    void rejectsExternalRelationshipEvenWithSurroundingWhitespace() {
        Map<String, byte[]> entries = baseEntries();
        entries.put("word/_rels/document.xml.rels", """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="r1" Target="https://attacker.invalid/payload"
                    TargetMode=" External "/>
                </Relationships>
                """.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator(defaults()).validate(zip("external.docx", entries),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Externe OOXML-Beziehungen");
    }

    @Test
    void rejectsMacrosAndEmbeddedObjects() {
        Map<String, byte[]> macroEntries = baseEntries();
        macroEntries.put("word/vbaProject.bin", new byte[]{1});
        Map<String, byte[]> oleEntries = baseEntries();
        oleEntries.put("word/embeddings/oleObject1.bin", new byte[]{1});

        assertThatThrownBy(() -> validator(defaults()).validate(zip("macro.docx", macroEntries),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Makros");
        assertThatThrownBy(() -> validator(defaults()).validate(zip("ole.docx", oleEntries),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("OLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "application/vnd.ms-office.vbaProject",
            "application/vnd.ms-office.vbaProjectSignature",
            "application/vnd.ms-word.vbaData+xml",
            "application/vnd.ms-excel.macrosheet+xml",
            "application/vnd.ms-excel.intlmacrosheet+xml",
            "application/vnd.ms-excel.sheet.macroEnabled.main+xml"
    })
    void rejectsMacroContentTypesAtArbitraryPartPaths(String macroContentType) {
        Map<String, byte[]> entries = baseEntries();
        entries.put("custom/innocent-looking.payload", new byte[]{1});
        String contentTypes = DOCX_CONTENT_TYPES.replace("</Types>", """
                <Override PartName="/custom/innocent-looking.payload" ContentType="%s"/>
                </Types>
                """.formatted(macroContentType));
        entries.put("[Content_Types].xml", contentTypes.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator(defaults()).validate(zip("macro-type.docx", entries),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Makros");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://schemas.microsoft.com/office/2006/relationships/vbaProject",
            "http://schemas.microsoft.com/office/2006/relationships/vbaProjectSignature",
            "http://schemas.microsoft.com/office/2006/relationships/vbaData",
            "http://schemas.microsoft.com/office/2006/relationships/xlMacrosheet",
            "http://schemas.microsoft.com/office/2006/relationships/xlIntlMacrosheet"
    })
    void rejectsMacroRelationshipTypesAtArbitraryPartPaths(String macroRelationshipType) {
        Map<String, byte[]> entries = baseEntries();
        entries.put("word/_rels/document.xml.rels", """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rMacro" Type="%s" Target="../custom/innocent-looking.payload"/>
                </Relationships>
                """.formatted(macroRelationshipType).getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator(defaults()).validate(zip("macro-relationship.docx", entries),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Makros");
    }

    @Test
    void rejectsDuplicateEntriesAndPathTraversal() {
        byte[] duplicate = zipWithDuplicateEntries();
        Map<String, byte[]> traversalEntries = baseEntries();
        traversalEntries.put("word/../payload.bin", new byte[]{1});

        assertThatThrownBy(() -> validator(defaults()).validate(upload("duplicate.docx", duplicate),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("doppelte ZIP-Einträge");
        assertThatThrownBy(() -> validator(defaults()).validate(zip("traversal.docx", traversalEntries),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Pfad-Traversal");
    }

    @Test
    void rejectsExpandedContentOverBudget() {
        DocumentProcessingProperties limits = new DocumentProcessingProperties(
                365, 200, 1_000_000, 2_000, 64, 100, 2_000, 4_000, Duration.ofSeconds(20));
        Map<String, byte[]> entries = baseEntries();
        entries.put("word/padding.bin", new byte[128]);

        assertThatThrownBy(() -> validator(limits).validate(zip("large.docx", entries),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("entpackte Office-Dokument");
    }

    @Test
    void rejectsCompressionBombAndExcessiveEntryCount() {
        DocumentProcessingProperties ratioLimits = new DocumentProcessingProperties(
                365, 200, 1_000_000, 2_000, 2_000_000, 5, 2_000, 4_000,
                Duration.ofSeconds(20));
        Map<String, byte[]> compressedEntries = baseEntries();
        compressedEntries.put("word/padding.bin", new byte[100_000]);

        assertThatThrownBy(() -> validator(ratioLimits).validate(zip("ratio.docx", compressedEntries),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Kompressionsverhältnis");

        DocumentProcessingProperties entryLimits = new DocumentProcessingProperties(
                365, 200, 1_000_000, 2_000, 2_000_000, 100, 2, 4_000,
                Duration.ofSeconds(20));
        assertThatThrownBy(() -> validator(entryLimits).validate(docx(Map.of()),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("zu viele ZIP-Einträge");
    }

    @Test
    void rejectsEncryptedPackageMarker() {
        Map<String, byte[]> entries = baseEntries();
        entries.put("EncryptionInfo", new byte[]{1});

        assertThatThrownBy(() -> validator(defaults()).validate(zip("encrypted.docx", entries),
                OoxmlPackageValidator.Kind.DOCX))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Verschlüsselte Office-Dokumente");
    }

    private OoxmlPackageValidator validator(DocumentProcessingProperties properties) {
        return new OoxmlPackageValidator(properties);
    }

    private DocumentProcessingProperties defaults() {
        return DocumentProcessingProperties.secureDefaults();
    }

    private MockMultipartFile docx(Map<String, byte[]> additional) {
        Map<String, byte[]> entries = baseEntries();
        entries.putAll(additional);
        return zip("document.docx", entries);
    }

    private Map<String, byte[]> baseEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", DOCX_CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
        entries.put("_rels/.rels", DOCX_RELATIONSHIPS.getBytes(StandardCharsets.UTF_8));
        entries.put("word/document.xml", "<document/>".getBytes(StandardCharsets.UTF_8));
        return entries;
    }

    private byte[] rootRelationships(String target, String additionalRelationships) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="r1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                    Target="%s"/>
                  %s
                </Relationships>
                """.formatted(target, additionalRelationships).getBytes(StandardCharsets.UTF_8);
    }

    private MockMultipartFile zip(String filename, Map<String, byte[]> entries) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return upload(filename, output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private byte[] zipWithDuplicateEntries() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            write(zip, "[Content_Types].xml", DOCX_CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
            write(zip, "_rels/.rels", DOCX_RELATIONSHIPS.getBytes(StandardCharsets.UTF_8));
            write(zip, "word/document.xml", new byte[]{1});
            // Unterschiedliche Schreibweise ist auf case-insensitiven Dateisystemen ebenfalls ein Duplikat.
            write(zip, "WORD/document.xml", new byte[]{2});
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void write(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private MockMultipartFile upload(String filename, byte[] bytes) {
        return new MockMultipartFile("evidence", filename,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);
    }
}
