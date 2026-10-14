package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import jakarta.validation.constraints.Size;

public record QuestionPatchRequest(
        @Size(max = 10_000, message = "Antwort darf höchstens 10.000 Zeichen enthalten.") String answer,
        ReviewStatus reviewStatus
) {
}
