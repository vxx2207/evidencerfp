package com.certaaccess.evidencerfp.analysis.infrastructure;

import com.certaaccess.evidencerfp.analysis.domain.AnalysisUpload;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisUploadKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisUploadStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void orphanSweepCandidatesContainOnlyOldUuidDirectories() throws Exception {
        AnalysisUploadStorage storage = new AnalysisUploadStorage(temporaryDirectory.toString());
        UUID oldAnalysis = UUID.randomUUID();
        UUID freshAnalysis = UUID.randomUUID();
        MockMultipartFile rfp = new MockMultipartFile(
                "rfp", "fragen.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});
        storage.store(oldAnalysis, rfp, List.of(), null);
        storage.store(freshAnalysis, rfp, List.of(), null);
        Files.createDirectory(temporaryDirectory.resolve("kein-analyseverzeichnis"));

        Instant cutoff = Instant.parse("2026-10-19T10:00:00Z");
        Files.setLastModifiedTime(temporaryDirectory.resolve(oldAnalysis.toString()),
                FileTime.from(cutoff.minusSeconds(1)));
        Files.setLastModifiedTime(temporaryDirectory.resolve(freshAnalysis.toString()),
                FileTime.from(cutoff.plusSeconds(1)));

        assertThat(storage.findStaleAnalysisDirectories(cutoff, 10)).containsExactly(oldAnalysis);
    }

    @Test
    void deleteIsIdempotent() {
        AnalysisUploadStorage storage = new AnalysisUploadStorage(temporaryDirectory.toString());
        UUID analysisId = UUID.randomUUID();
        MockMultipartFile rfp = new MockMultipartFile("rfp", "fragen.xlsx", null, new byte[]{1});
        storage.store(analysisId, rfp, List.of(), null);

        storage.delete(analysisId);
        storage.delete(analysisId);

        assertThat(temporaryDirectory.resolve(analysisId.toString())).doesNotExist();
    }

    @Test
    void neverOpensMetadataPathOutsideConfiguredStorageRoot() {
        AnalysisUploadStorage storage = new AnalysisUploadStorage(temporaryDirectory.toString());
        UUID analysisId = UUID.randomUUID();
        AnalysisUpload forged = new AnalysisUpload(UUID.randomUUID(), analysisId,
                AnalysisUploadKind.EVIDENCE, 0, "beleg.pdf", "application/pdf", 1,
                "00".repeat(32), "../fremd.pdf");

        assertThatThrownBy(() -> storage.open(forged))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ungültiger Speicherpfad");
    }
}
