package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;

public final class AnalysisNotReadyException extends RuntimeException {
    public AnalysisNotReadyException(AnalysisStatus status) {
        super("Die Analyse ist noch nicht abgeschlossen (Status: " + status + ").");
    }
}
