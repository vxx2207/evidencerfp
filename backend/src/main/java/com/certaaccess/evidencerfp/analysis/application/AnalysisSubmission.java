package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;

public record AnalysisSubmission(Analysis analysis, boolean created) {
}
