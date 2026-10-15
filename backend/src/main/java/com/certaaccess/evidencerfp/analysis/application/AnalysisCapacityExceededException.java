package com.certaaccess.evidencerfp.analysis.application;

public final class AnalysisCapacityExceededException extends RuntimeException {
    public AnalysisCapacityExceededException() {
        super("Die lokale Analysewarteschlange ist ausgelastet. Bitte den laufenden Job abwarten.");
    }
}
