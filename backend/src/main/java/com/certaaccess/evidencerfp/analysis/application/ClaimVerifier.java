package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.evidence.CitationVerifier;
import com.certaaccess.evidencerfp.evidence.EvidenceChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ClaimVerifier {
    private final CitationVerifier citationVerifier;

    public ClaimVerifier(CitationVerifier citationVerifier) {
        this.citationVerifier = citationVerifier;
    }

    public ClaimVerification verify(List<GeneratedClaim> claims, List<EvidenceChunk> retrievedChunks) {
        List<GeneratedClaim> verified = new ArrayList<>();
        List<GeneratedClaim> rejected = new ArrayList<>();
        for (GeneratedClaim claim : claims == null ? List.<GeneratedClaim>of() : claims) {
            if (claim != null && citationVerifier.verifyClaim(
                    claim.text(), claim.citations(), retrievedChunks)) {
                verified.add(claim);
            } else if (claim != null) {
                rejected.add(claim);
            }
        }
        return new ClaimVerification(verified, rejected);
    }
}
