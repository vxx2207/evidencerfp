package com.certaaccess.evidencerfp.analysis.infrastructure;

import com.certaaccess.evidencerfp.analysis.application.AnalysisStatusSnapshot;
import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJob;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisPhase;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAnalysisRepositoryTest {

    @Test
    void expiredLeaseCannotBeRenewedAdvancedCompletedOrFailed() {
        InMemoryAnalysisRepository repository = new InMemoryAnalysisRepository(new DemoAnalysisFactory());
        Instant claimedAt = Instant.parse("2026-10-19T08:00:00Z");
        Instant expiresAt = claimedAt.plusSeconds(30);
        Analysis processing = processingAnalysis(claimedAt);
        repository.enqueue(processing, AnalysisJob.queued(processing.id(), claimedAt, 2),
                List.of(), null, "a".repeat(64));
        assertThat(repository.claimJob(processing.id(), "worker-a", claimedAt, expiresAt)).isPresent();

        Analysis completed = new Analysis(processing.id(), processing.customerName(), false,
                AnalysisStatus.COMPLETED, claimedAt, expiresAt, List.of(), List.of(), List.of());

        assertThat(repository.renewLease(processing.id(), "worker-a", expiresAt,
                expiresAt.plusSeconds(30))).isFalse();
        assertThat(repository.advanceJob(processing.id(), "worker-a", AnalysisPhase.ANSWERING,
                55, expiresAt, expiresAt.plusSeconds(30))).isFalse();
        assertThat(repository.completeJob(completed, "worker-a", expiresAt)).isFalse();
        assertThat(repository.failJob(processing.id(), "worker-a", "SHOULD_NOT_WIN", expiresAt)).isFalse();

        assertThat(repository.findStatusSnapshotById(processing.id())).get()
                .satisfies(snapshot -> assertUnchangedProcessingSnapshot(snapshot, expiresAt));
    }

    @Test
    void statusSnapshotContainsAnalysisAndJobFromOneSynchronizedRead() {
        InMemoryAnalysisRepository repository = new InMemoryAnalysisRepository(new DemoAnalysisFactory());
        Instant now = Instant.parse("2026-10-19T09:00:00Z");
        Analysis processing = processingAnalysis(now);
        repository.enqueue(processing, AnalysisJob.queued(processing.id(), now, 2),
                List.of(), null, "b".repeat(64));

        assertThat(repository.findStatusSnapshotById(processing.id())).get().satisfies(snapshot -> {
            assertThat(snapshot.analysis()).isSameAs(processing);
            assertThat(snapshot.job()).get().extracting(AnalysisJob::analysisId).isEqualTo(processing.id());
        });
    }

    @Test
    void cleanupLeaseCanBeRecoveredAndOnlyCurrentOwnerCanComplete() {
        InMemoryAnalysisRepository repository = new InMemoryAnalysisRepository(new DemoAnalysisFactory());
        Instant now = Instant.parse("2026-10-19T09:30:00Z");
        Analysis processing = processingAnalysis(now);
        repository.enqueue(processing, AnalysisJob.queued(processing.id(), now, 2),
                List.of(), null, "c".repeat(64));
        assertThat(repository.claimJob(processing.id(), "worker", now, now.plusSeconds(30))).isPresent();
        Analysis completed = new Analysis(processing.id(), processing.customerName(), false,
                AnalysisStatus.COMPLETED, now, now.plusSeconds(1), List.of(), List.of(), List.of());
        assertThat(repository.completeJob(completed, "worker", now.plusSeconds(1))).isTrue();

        assertThat(repository.claimCleanupTasks("cleanup-a", now.plusSeconds(1),
                now.plusSeconds(31), 10)).containsExactly(processing.id());
        assertThat(repository.claimCleanupTasks("cleanup-b", now.plusSeconds(30),
                now.plusSeconds(60), 10)).isEmpty();
        assertThat(repository.claimCleanupTasks("cleanup-b", now.plusSeconds(32),
                now.plusSeconds(62), 10)).containsExactly(processing.id());
        assertThat(repository.completeCleanup(processing.id(), "cleanup-a", now.plusSeconds(33))).isFalse();
        assertThat(repository.completeCleanup(processing.id(), "cleanup-b", now.plusSeconds(33))).isTrue();
    }

    @Test
    void deleteHidesAnalysisButKeepsCancellationTombstoneUntilCleanupCompletes() {
        InMemoryAnalysisRepository repository = new InMemoryAnalysisRepository(new DemoAnalysisFactory());
        Instant now = Instant.parse("2026-10-19T10:00:00Z");
        Analysis processing = processingAnalysis(now);
        repository.enqueue(processing, AnalysisJob.queued(processing.id(), now, 2),
                List.of(), null, "e".repeat(64));
        assertThat(repository.claimJob(processing.id(), "worker", now, now.plusSeconds(30))).isPresent();

        assertThat(repository.deleteById(processing.id(), now.plusSeconds(1))).isTrue();
        assertThat(repository.findById(processing.id())).isEmpty();
        assertThat(repository.findJobByAnalysisId(processing.id())).get()
                .satisfies(job -> {
                    assertThat(job.status()).isEqualTo(
                            com.certaaccess.evidencerfp.analysis.domain.AnalysisJobStatus.CANCELLED);
                    assertThat(job.cancelRequested()).isTrue();
                });
        assertThat(repository.claimCleanupTasks("cleanup", now.plusSeconds(1),
                now.plusSeconds(31), 10)).containsExactly(processing.id());
        assertThat(repository.completeCleanup(processing.id(), "cleanup", now.plusSeconds(2))).isTrue();
        assertThat(repository.findJobByAnalysisId(processing.id())).isEmpty();
    }

    @Test
    void deleteReactivatesAnAlreadyCompletedCleanupTaskForPhysicalPurge() {
        InMemoryAnalysisRepository repository = new InMemoryAnalysisRepository(new DemoAnalysisFactory());
        Instant now = Instant.parse("2026-10-19T10:30:00Z");
        Analysis processing = processingAnalysis(now);
        repository.enqueue(processing, AnalysisJob.queued(processing.id(), now, 2),
                List.of(), null, "f".repeat(64));
        assertThat(repository.claimJob(processing.id(), "worker", now, now.plusSeconds(30))).isPresent();
        Analysis completed = new Analysis(processing.id(), processing.customerName(), false,
                AnalysisStatus.COMPLETED, now, now.plusSeconds(1), List.of(), List.of(), List.of());
        assertThat(repository.completeJob(completed, "worker", now.plusSeconds(1))).isTrue();
        assertThat(repository.claimCleanupTasks("terminal-cleanup", now.plusSeconds(1),
                now.plusSeconds(31), 10)).containsExactly(processing.id());
        assertThat(repository.completeCleanup(processing.id(), "terminal-cleanup",
                now.plusSeconds(2))).isTrue();

        assertThat(repository.deleteById(processing.id(), now.plusSeconds(3))).isTrue();
        assertThat(repository.claimCleanupTasks("delete-cleanup", now.plusSeconds(3),
                now.plusSeconds(33), 10)).containsExactly(processing.id());
        assertThat(repository.completeCleanup(processing.id(), "delete-cleanup",
                now.plusSeconds(4))).isTrue();

        assertThat(repository.findById(processing.id())).isEmpty();
        assertThat(repository.findJobByAnalysisId(processing.id())).isEmpty();
    }

    private void assertUnchangedProcessingSnapshot(AnalysisStatusSnapshot snapshot, Instant expiresAt) {
        assertThat(snapshot.analysis().status()).isEqualTo(AnalysisStatus.PROCESSING);
        assertThat(snapshot.job()).get().satisfies(job -> {
            assertThat(job.phase()).isEqualTo(AnalysisPhase.PARSING);
            assertThat(job.progressPercent()).isEqualTo(5);
            assertThat(job.leaseUntil()).isEqualTo(expiresAt);
            assertThat(job.failureCode()).isNull();
        });
    }

    private Analysis processingAnalysis(Instant createdAt) {
        return new Analysis(UUID.randomUUID(), "Lease Test", false, AnalysisStatus.PROCESSING,
                createdAt, null, List.of(), List.of(), List.of());
    }
}
