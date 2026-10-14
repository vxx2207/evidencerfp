package com.certaaccess.evidencerfp.analysis.domain;

import java.util.UUID;

public record Finding(
        UUID id,
        FindingType type,
        FindingSeverity severity,
        String ruleId,
        String title,
        String description,
        String affectedObject,
        String recommendation,
        boolean blocksApproval
) {
    public static Finding of(FindingType type, FindingSeverity severity, String ruleId, String title,
                             String description, String affectedObject, String recommendation,
                             boolean blocksApproval) {
        return new Finding(UUID.randomUUID(), type, severity, ruleId, title, description,
                affectedObject, recommendation, blocksApproval);
    }
}
