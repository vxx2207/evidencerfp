package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.application.AnalysisNotFoundException;
import com.certaaccess.evidencerfp.analysis.application.AnalysisCapacityExceededException;
import com.certaaccess.evidencerfp.analysis.domain.ApprovalBlockedException;
import com.certaaccess.evidencerfp.analysis.domain.ReviewIncompleteException;
import com.certaaccess.evidencerfp.evidence.InvalidUploadException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({InvalidUploadException.class, MethodArgumentNotValidException.class})
    ProblemDetail badRequest(Exception exception, HttpServletRequest request) {
        String detail = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getAllErrors().stream().findFirst()
                    .map(error -> error.getDefaultMessage()).orElse("Ungültige Anfrage.")
                : exception.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "Ungültige Eingabe", detail, "INVALID_INPUT", request);
    }

    @ExceptionHandler(AnalysisNotFoundException.class)
    ProblemDetail notFound(AnalysisNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Analyse nicht gefunden", exception.getMessage(),
                "ANALYSIS_NOT_FOUND", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail questionNotFound(IllegalArgumentException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Ressource nicht gefunden", exception.getMessage(),
                "RESOURCE_NOT_FOUND", request);
    }

    @ExceptionHandler(ApprovalBlockedException.class)
    ProblemDetail conflict(ApprovalBlockedException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Freigabe blockiert", exception.getMessage(),
                "APPROVAL_BLOCKED", request);
    }

    @ExceptionHandler(ReviewIncompleteException.class)
    ProblemDetail reviewIncomplete(ReviewIncompleteException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Review unvollständig", exception.getMessage(),
                "REVIEW_INCOMPLETE", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Upload zu groß",
                "Der Upload überschreitet das konfigurierte Größenlimit.", "UPLOAD_TOO_LARGE", request);
    }

    @ExceptionHandler(AnalysisCapacityExceededException.class)
    ProblemDetail capacityExceeded(AnalysisCapacityExceededException exception, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Analysewarteschlange ausgelastet",
                exception.getMessage(), "ANALYSIS_CAPACITY_EXCEEDED", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail internalError(IllegalStateException exception, HttpServletRequest request) {
        LOGGER.error("Unerwarteter API-Fehler bei {}", request.getRequestURI(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Interner Fehler",
                "Die Anfrage konnte wegen eines internen Fehlers nicht verarbeitet werden.",
                "INTERNAL_ERROR", request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String code,
                                  HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://evidence-rfp.local/problems/" + code.toLowerCase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
