package com.certaaccess.evidencerfp.analysis.infrastructure;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Initialisiert den synthetischen Backend-Datensatz genau einmal pro Datenbank. */
@Component
@Profile("production")
final class DemoDataInitializer {
    private final JdbcAnalysisRepository repository;

    DemoDataInitializer(JdbcAnalysisRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    void initialize() {
        repository.seedDemoOnce();
    }
}
