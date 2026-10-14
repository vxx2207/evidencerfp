package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.application.AnalysisNotFoundException;
import com.certaaccess.evidencerfp.evidence.InvalidUploadException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {

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

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Freigabe blockiert", exception.getMessage(),
                "APPROVAL_BLOCKED", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Upload zu groß",
                "Der Upload überschreitet das konfigurierte Größenlimit.", "UPLOAD_TOO_LARGE", request);
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
