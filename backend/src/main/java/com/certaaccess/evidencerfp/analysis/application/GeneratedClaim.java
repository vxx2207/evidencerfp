package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.Citation;

import java.util.List;

/** Einzelne Modellaussage mit den ausschließlich für diese Aussage geltenden Belegen. */
public record GeneratedClaim(String text, List<Citation> citations) {
    public GeneratedClaim {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
