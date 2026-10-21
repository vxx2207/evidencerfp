package com.certaaccess.evidencerfp.analysis.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record CapabilitiesResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String mode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String aiProvider,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean aiAvailable,
        long maxFileSizeBytes,
        List<String> supportedRfpTypes,
        List<String> supportedEvidenceTypes,
        List<String> iamRules,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Limits limits,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Models models,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String retrievalVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String dataBoundary,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> dataFlow,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID demoAnalysisId
) {
    public record Limits(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long maxFileBytes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long maxTotalBytes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int maxEvidenceFiles,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int maxPdfPages,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int maxExtractedCharacters,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int maxChunksPerFile,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int maxRfpQuestions,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int maxQuestionCharacters,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long maxOoxmlExpandedBytes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            double maxOoxmlCompressionRatio,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int maxOoxmlEntries,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long parserTimeoutSeconds
    ) { }

    public record Models(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String chat,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String embeddings
    ) { }
}
