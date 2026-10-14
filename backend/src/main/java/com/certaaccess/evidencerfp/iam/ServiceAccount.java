package com.certaaccess.evidencerfp.iam;

import java.time.Instant;

public record ServiceAccount(
        String id,
        String displayName,
        boolean enabled,
        Instant credentialLastRotatedAt,
        String ownerId
) {
}
