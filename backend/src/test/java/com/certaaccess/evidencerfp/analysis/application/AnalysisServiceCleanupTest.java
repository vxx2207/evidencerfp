package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJob;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.infrastructure.AnalysisUploadStorage;
import com.certaaccess.evidencerfp.analysis.infrastructure.InMemoryAnalysisRepository;
import com.certaaccess.evidencerfp.evidence.EvidenceExtractor;
import com.certaaccess.evidencerfp.evidence.EvidenceRetriever;
import com.certaaccess.evidencerfp.evidence.RfpParser;
import com.certaaccess.evidencerfp.evidence.UploadValidator;
import com.certaaccess.evidencerfp.iam.IamControlEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalysisServiceCleanupTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void failedFilesystemCleanupRemainsPersistedForRetry() {
        Instant now = Instant.parse("2026-10-19T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryAnalysisRepository repository = new InMemoryAnalysisRepository(new DemoAnalysisFactory());
        UUID analysisId = UUID.randomUUID();
        Analysis processing = new Analysis(analysisId, "Cleanup Test", false,
                AnalysisStatus.PROCESSING, now, null, List.of(), List.of(), List.of());
        repository.enqueue(processing, AnalysisJob.queued(analysisId, now, 2),
                List.of(), null, "d".repeat(64));
        assertThat(repository.claimJob(analysisId, "analysis-worker", now, now.plusSeconds(30)))
                .isPresent();
        Analysis completed = new Analysis(analysisId, processing.customerName(), false,
                AnalysisStatus.COMPLETED, now, now, List.of(), List.of(), List.of());
        assertThat(repository.completeJob(completed, "analysis-worker", now)).isTrue();

        AnalysisService service = new AnalysisService(repository, mock(UploadValidator.class),
                mock(RfpParser.class), mock(EvidenceExtractor.class), mock(EvidenceRetriever.class),
                mock(AnswerGenerator.class), new IamControlEvaluator(clock), new ObjectMapper(), clock,
                Runnable::run, null, new AlwaysFailingStorage(temporaryDirectory), 30, 10, 2, 8);

        service.recoverRunnableJobs();

        assertThat(repository.claimCleanupTasks("too-early", now.plusSeconds(29),
                now.plusSeconds(59), 10)).isEmpty();
        assertThat(repository.claimCleanupTasks("recovery-worker", now.plusSeconds(31),
                now.plusSeconds(61), 10)).containsExactly(analysisId);
    }

    @Test
    void orphanSweepDeletesOnlyDirectoriesWithoutPersistentOwnership() {
        Instant now = Instant.parse("2026-10-19T11:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryAnalysisRepository repository = new InMemoryAnalysisRepository(new DemoAnalysisFactory());
        UUID orphanId = UUID.randomUUID();
        List<UUID> candidates = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            UUID trackedId = UUID.randomUUID();
            candidates.add(trackedId);
            repository.save(new Analysis(trackedId, "Noch vorhanden", false,
                    AnalysisStatus.COMPLETED, now.minusSeconds(7_200), now.minusSeconds(7_100),
                    List.of(), List.of(), List.of()));
        }
        candidates.add(orphanId);
        RecordingStorage storage = new RecordingStorage(temporaryDirectory, candidates);
        AnalysisService service = new AnalysisService(repository, mock(UploadValidator.class),
                mock(RfpParser.class), mock(EvidenceExtractor.class), mock(EvidenceRetriever.class),
                mock(AnswerGenerator.class), new IamControlEvaluator(clock), new ObjectMapper(), clock,
                Runnable::run, null, storage, 30, 10, 2, 8);

        service.recoverRunnableJobs();

        assertThat(storage.deleted).containsExactly(orphanId);
    }

    private static final class AlwaysFailingStorage extends AnalysisUploadStorage {
        private AlwaysFailingStorage(Path root) {
            super(root.toString());
        }

        @Override
        public void delete(UUID analysisId) {
            throw new IllegalStateException("simulierter Dateisystemfehler");
        }

        @Override
        public Stream<UUID> streamStaleAnalysisDirectories(Instant olderThan) {
            return Stream.empty();
        }
    }

    private static final class RecordingStorage extends AnalysisUploadStorage {
        private final List<UUID> candidates;
        private final List<UUID> deleted = new ArrayList<>();

        private RecordingStorage(Path root, List<UUID> candidates) {
            super(root.toString());
            this.candidates = candidates;
        }

        @Override
        public void delete(UUID analysisId) {
            deleted.add(analysisId);
        }

        @Override
        public Stream<UUID> streamStaleAnalysisDirectories(Instant olderThan) {
            return candidates.stream();
        }
    }
}
