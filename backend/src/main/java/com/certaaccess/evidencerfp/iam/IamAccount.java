package com.certaaccess.evidencerfp.iam;

import java.util.List;

public record IamAccount(
        String id,
        String displayName,
        boolean privileged,
        boolean enabled,
        List<String> mfaMethods,
        String ownerId
) {
    public IamAccount {
        mfaMethods = mfaMethods == null ? List.of() : List.copyOf(mfaMethods);
    }
}
