package com.certaaccess.evidencerfp.analysis.infrastructure;

import com.certaaccess.evidencerfp.analysis.application.AnalysisRepository;
import com.certaaccess.evidencerfp.analysis.application.DemoAnalysisFactory;
import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@Profile("!production")
public class InMemoryAnalysisRepository implements AnalysisRepository {
    private final ConcurrentMap<UUID, Analysis> analyses = new ConcurrentHashMap<>();
    private final DemoAnalysisFactory demoAnalysisFactory;

    public InMemoryAnalysisRepository(DemoAnalysisFactory demoAnalysisFactory) {
        this.demoAnalysisFactory = demoAnalysisFactory;
    }

    @PostConstruct
    void seedDemo() {
        Analysis demo = demoAnalysisFactory.create();
        analyses.put(demo.id(), demo);
    }

    @Override
    public Analysis save(Analysis analysis) {
        analyses.put(analysis.id(), analysis);
        return analysis;
    }

    @Override
    public Optional<Analysis> findById(UUID id) {
        return Optional.ofNullable(analyses.get(id));
    }

    @Override
    public boolean deleteById(UUID id) {
        return analyses.remove(id) != null;
    }
}
