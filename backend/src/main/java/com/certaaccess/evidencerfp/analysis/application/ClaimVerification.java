package com.certaaccess.evidencerfp.analysis.application;

import java.util.List;

public record ClaimVerification(List<GeneratedClaim> verified, List<GeneratedClaim> rejected) {
    public ClaimVerification {
        verified = verified == null ? List.of() : List.copyOf(verified);
        rejected = rejected == null ? List.of() : List.copyOf(rejected);
    }

    public boolean complete() {
        return !verified.isEmpty() && rejected.isEmpty();
    }
}
