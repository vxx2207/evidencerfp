package com.certaaccess.evidencerfp.analysis.api;

import java.util.List;
import java.util.UUID;

public record CapabilitiesResponse(
        String mode,
        String aiProvider,
        boolean aiAvailable,
        long maxFileSizeBytes,
        List<String> supportedRfpTypes,
        List<String> supportedEvidenceTypes,
        List<String> iamRules,
        UUID demoAnalysisId
) {
}
