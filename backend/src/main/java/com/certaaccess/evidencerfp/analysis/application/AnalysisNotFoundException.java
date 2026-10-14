package com.certaaccess.evidencerfp.analysis.application;

import java.util.UUID;

public class AnalysisNotFoundException extends RuntimeException {
    public AnalysisNotFoundException(UUID id) {
        super("Analyse wurde nicht gefunden: " + id);
    }
}
