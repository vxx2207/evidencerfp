package com.certaaccess.evidencerfp.analysis.domain;

public enum AnalysisPhase {
    QUEUED,
    PARSING,
    INDEXING,
    ANSWERING,
    IAM_CHECKS,
    PERSISTING,
    COMPLETED,
    FAILED,
    CANCELLED
}
