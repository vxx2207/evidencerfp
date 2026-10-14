package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import com.certaaccess.evidencerfp.analysis.infrastructure.JdbcAnalysisRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PgVectorEvidenceRetrieverIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Test
    void migratesSchemaAndRetrievesNearestChunkWithPgvectorOperator() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        PgVectorEvidenceRetriever retriever = new PgVectorEvidenceRetriever(
                JdbcClient.create(dataSource), new HashingEvidenceEmbeddingProvider());
        EvidenceChunk relevant = chunk("FIDO2 Passkeys phishing-resistente Authentifizierung");
        EvidenceChunk unrelated = chunk("Backups und Wiederherstellung werden jährlich getestet");

        List<EvidenceChunk> result = retriever.retrieve(
                new RfpQuestion("RFP-001", "Werden FIDO2 Passkeys unterstützt?", "Identität"),
                List.of(unrelated, relevant), 1);

        assertThat(result).extracting(EvidenceChunk::id).containsExactly(relevant.id());
    }

    @Test
    void persistsAndReloadsTheCompleteReviewState() {
        PGSimpleDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcAnalysisRepository repository = new JdbcAnalysisRepository(
                JdbcClient.create(dataSource), new ObjectMapper(), new DemoAnalysisFactory());
        Analysis analysis = new DemoAnalysisFactory().create();
        analysis.updateQuestion("RFP-001", null, ReviewStatus.APPROVED);

        repository.save(analysis);
        Analysis reloaded = repository.findById(analysis.id()).orElseThrow();

        assertThat(reloaded.questions()).hasSize(24);
        assertThat(reloaded.evidenceDocuments()).hasSize(5);
        assertThat(reloaded.findings()).hasSize(6);
        assertThat(reloaded.questions().getFirst().reviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private EvidenceChunk chunk(String content) {
        return new EvidenceChunk(UUID.randomUUID(), "test.pdf", "application/pdf", "Seite 1", content, null);
    }
}
