package com.certaaccess.evidencerfp.analysis.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;

/**
 * OpenAPI representation of the RFC-7807 payload emitted by {@link ApiExceptionHandler}.
 * Spring's {@code ProblemDetail} cannot describe the application-specific {@code code}
 * extension by itself, so this documentation-only DTO keeps the generated contract honest.
 */
@Schema(name = "Problem")
public record ApiProblemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        URI type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detail,
        URI instance,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String code
) {
}
