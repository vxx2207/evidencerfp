package com.certaaccess.evidencerfp.analysis.api;

import com.certaaccess.evidencerfp.analysis.application.AnalysisNotFoundException;
import com.certaaccess.evidencerfp.analysis.application.AnalysisNotReadyException;
import com.certaaccess.evidencerfp.analysis.application.AnalysisCapacityExceededException;
import com.certaaccess.evidencerfp.analysis.application.EditConflictException;
import com.certaaccess.evidencerfp.analysis.application.IdempotencyKeyReusedException;
import com.certaaccess.evidencerfp.analysis.application.PreconditionRequiredException;
import com.certaaccess.evidencerfp.analysis.domain.ApprovalBlockedException;
import com.certaaccess.evidencerfp.analysis.domain.ReviewIncompleteException;
import com.certaaccess.evidencerfp.evidence.InvalidUploadException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import com.certaaccess.evidencerfp.observability.EvidenceRfpMetrics;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final EvidenceRfpMetrics metrics;

    public ApiExceptionHandler() {
        this.metrics = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ApiExceptionHandler(EvidenceRfpMetrics metrics) {
        this.metrics = metrics;
    }

    @ExceptionHandler({InvalidUploadException.class, MethodArgumentNotValidException.class})
    ProblemDetail badRequest(Exception exception, HttpServletRequest request) {
        String detail = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getAllErrors().stream().findFirst()
                    .map(error -> error.getDefaultMessage()).orElse("Ungültige Anfrage.")
                : exception.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "Ungültige Eingabe", detail, "INVALID_INPUT", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ProblemDetail malformedRequest(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Ungültige Anfrage",
                "Die Anfrage ist unvollständig oder syntaktisch ungültig.",
                "INVALID_REQUEST", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail unsupportedMediaType(HttpMediaTypeNotSupportedException exception,
                                       HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Nicht unterstützter Medientyp",
                "Der Content-Type der Anfrage wird für diesen Endpunkt nicht unterstützt.",
                "UNSUPPORTED_MEDIA_TYPE", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ProblemDetail notAcceptable(HttpMediaTypeNotAcceptableException exception,
                                HttpServletRequest request) {
        return problem(HttpStatus.NOT_ACCEPTABLE, "Nicht akzeptables Antwortformat",
                "Für das angeforderte Antwortformat ist keine Darstellung verfügbar.",
                "NOT_ACCEPTABLE", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> methodNotAllowed(HttpRequestMethodNotSupportedException exception,
                                                   HttpServletRequest request) {
        ProblemDetail body = problem(HttpStatus.METHOD_NOT_ALLOWED, "Methode nicht erlaubt",
                "Die HTTP-Methode wird für diesen Endpunkt nicht unterstützt.",
                "METHOD_NOT_ALLOWED", request);
        var response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (exception.getSupportedHttpMethods() != null) {
            response.allow(exception.getSupportedHttpMethods().toArray(HttpMethod[]::new));
        }
        return response.body(body);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ProblemDetail apiResourceNotFound(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "API-Ressource nicht gefunden",
                "Für den angeforderten API-Pfad ist keine Ressource vorhanden.",
                "API_RESOURCE_NOT_FOUND", request);
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

    @ExceptionHandler(AnalysisNotReadyException.class)
    ProblemDetail analysisNotReady(AnalysisNotReadyException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Analyse noch nicht abgeschlossen", exception.getMessage(),
                "ANALYSIS_NOT_READY", request);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail idempotencyConflict(IdempotencyKeyReusedException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Idempotency-Key bereits verwendet", exception.getMessage(),
                "IDEMPOTENCY_KEY_REUSED", request);
    }

    @ExceptionHandler(EditConflictException.class)
    ProblemDetail editConflict(EditConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.PRECONDITION_FAILED, "Review-Konflikt", exception.getMessage(),
                "EDIT_CONFLICT", request);
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    ProblemDetail preconditionRequired(PreconditionRequiredException exception, HttpServletRequest request) {
        return problem(HttpStatus.PRECONDITION_REQUIRED, "Revision erforderlich", exception.getMessage(),
                "PRECONDITION_REQUIRED", request);
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
        LOGGER.atError().addKeyValue("requestPath", request.getRequestURI())
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .log("Unerwarteter API-Fehler");
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Interner Fehler",
                "Die Anfrage konnte wegen eines internen Fehlers nicht verarbeitet werden.",
                "INTERNAL_ERROR", request);
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail persistenceError(DataAccessException exception, HttpServletRequest request) {
        LOGGER.atError().addKeyValue("requestPath", request.getRequestURI())
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .log("Persistenzfehler an der API-Grenze");
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Interner Fehler",
                "Die Anfrage konnte wegen eines internen Fehlers nicht verarbeitet werden.",
                "INTERNAL_ERROR", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpectedError(Exception exception, HttpServletRequest request) {
        LOGGER.atError().addKeyValue("requestPath", request.getRequestURI())
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .log("Unerwarteter unbehandelter API-Fehler");
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
        if (metrics != null) metrics.error(code);
        return problem;
    }
}
