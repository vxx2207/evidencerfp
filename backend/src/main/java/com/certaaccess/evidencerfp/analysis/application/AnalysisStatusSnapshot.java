package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisJob;

import java.util.Objects;
import java.util.Optional;

/**
 * A transactionally consistent view of an analysis and its persistent job.
 * Legacy synthetic analyses intentionally have no job and derive one at the API boundary.
 */
public record AnalysisStatusSnapshot(Analysis analysis, Optional<AnalysisJob> job) {
    public AnalysisStatusSnapshot {
        Objects.requireNonNull(analysis, "analysis");
        job = job == null ? Optional.empty() : job;
    }
}
