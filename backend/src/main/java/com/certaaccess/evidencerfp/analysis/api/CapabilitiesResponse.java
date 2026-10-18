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
        Limits limits,
        Models models,
        String retrievalVersion,
        String dataBoundary,
        List<String> dataFlow,
        UUID demoAnalysisId
) {
    public record Limits(
            long maxFileBytes,
            long maxTotalBytes,
            int maxEvidenceFiles,
            int maxPdfPages,
            int maxExtractedCharacters,
            int maxChunksPerFile,
            int maxRfpQuestions,
            int maxQuestionCharacters,
            long maxOoxmlExpandedBytes,
            double maxOoxmlCompressionRatio,
            int maxOoxmlEntries,
            long parserTimeoutSeconds
    ) { }

    public record Models(String chat, String embeddings) { }
}
