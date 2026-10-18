package com.certaaccess.evidencerfp.analysis.domain;

import java.util.UUID;

public record AnalysisUpload(
        UUID id,
        UUID analysisId,
        AnalysisUploadKind kind,
        int position,
        String originalFilename,
        String mediaType,
        long sizeBytes,
        String sha256,
        String storagePath
) {
}
