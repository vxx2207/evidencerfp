package com.certaaccess.evidencerfp.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("evidence-rfp.uploads")
public record UploadValidationProperties(
        long maxFileBytes,
        long maxTotalBytes,
        int maxEvidenceFiles
) {
}
