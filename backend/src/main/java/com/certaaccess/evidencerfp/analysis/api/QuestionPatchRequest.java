package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(minProperties = 1)
public record QuestionPatchRequest(
        @Size(max = 10_000, message = "Antwort darf höchstens 10.000 Zeichen enthalten.") String answer,
        ReviewStatus reviewStatus
) {
}
