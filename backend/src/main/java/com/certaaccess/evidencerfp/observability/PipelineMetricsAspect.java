package com.certaaccess.evidencerfp.observability;

import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.application.AnswerGenerationException;
import com.certaaccess.evidencerfp.analysis.application.ClaimVerification;
import com.certaaccess.evidencerfp.evidence.InvalidUploadException;
import com.certaaccess.evidencerfp.evidence.EvidenceProviderException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PipelineMetricsAspect {
    private final EvidenceRfpMetrics metrics;

    public PipelineMetricsAspect(EvidenceRfpMetrics metrics) {
        this.metrics = metrics;
    }

    @Around("execution(* com.certaaccess.evidencerfp.evidence.RfpParser.parse(..)) || "
            + "execution(* com.certaaccess.evidencerfp.evidence.EvidenceExtractor.extract(..)) || "
            + "execution(* com.certaaccess.evidencerfp.evidence.UploadValidator.validate(..))")
    public Object parser(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return timed(joinPoint, "parsing");
        } catch (InvalidUploadException exception) {
            metrics.parserRejected(joinPoint.getSignature().getDeclaringType().getSimpleName());
            throw exception;
        }
    }

    @Around("execution(* com.certaaccess.evidencerfp.evidence.EvidenceChunker.chunk(..))")
    public Object chunking(ProceedingJoinPoint joinPoint) throws Throwable {
        return timed(joinPoint, "chunking");
    }

    @Around("execution(* com.certaaccess.evidencerfp.evidence.EvidenceEmbeddingProvider+.embedBatch(..))")
    public Object embedding(ProceedingJoinPoint joinPoint) throws Throwable {
        return timed(joinPoint, "embedding");
    }

    @Around("execution(* com.certaaccess.evidencerfp.evidence.EvidenceRetriever+.retrieveBatchWithProvenance(..))")
    public Object retrieval(ProceedingJoinPoint joinPoint) throws Throwable {
        return timed(joinPoint, "retrieval");
    }

    @Around("execution(* com.certaaccess.evidencerfp.analysis.application.AnswerGenerator+.generate(..))")
    public Object generation(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = timed(joinPoint, "generation");
        if (result instanceof QuestionResult question) metrics.answer(question.answerStatus());
        return result;
    }

    @Around("execution(* com.certaaccess.evidencerfp.evidence.CitationVerifier.verify*(..)) || "
            + "execution(* com.certaaccess.evidencerfp.analysis.application.ClaimVerifier.verify(..))")
    public Object verification(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = timed(joinPoint, "verification");
        if (result instanceof Boolean valid && !valid) {
            metrics.rejectedCitation(joinPoint.getSignature().getName());
        } else if (result instanceof ClaimVerification claims && !claims.rejected().isEmpty()) {
            metrics.rejectedCitation("claim", claims.rejected().size());
        }
        return result;
    }

    @Around("execution(* com.certaaccess.evidencerfp.analysis.application.AnalysisExporter.toXlsx(..))")
    public Object export(ProceedingJoinPoint joinPoint) throws Throwable {
        return timed(joinPoint, "export");
    }

    private Object timed(ProceedingJoinPoint joinPoint, String stage) throws Throwable {
        try {
            return metrics.timed(stage, () -> {
                try {
                    return joinPoint.proceed();
                } catch (RuntimeException | Error exception) {
                    throw exception;
                } catch (Throwable throwable) {
                    throw new WrappedThrowable(throwable);
                }
            });
        } catch (WrappedThrowable wrapped) {
            metrics.error("PIPELINE_" + stage.toUpperCase(java.util.Locale.ROOT) + "_FAILED");
            throw wrapped.getCause();
        } catch (RuntimeException exception) {
            String code = exception instanceof AnswerGenerationException generation
                    ? generation.code()
                    : exception instanceof EvidenceProviderException provider
                    ? provider.code()
                    : exception instanceof InvalidUploadException
                    ? "DOCUMENT_REJECTED"
                    : "PIPELINE_" + stage.toUpperCase(java.util.Locale.ROOT) + "_FAILED";
            metrics.error(code);
            throw exception;
        }
    }

    private static final class WrappedThrowable extends RuntimeException {
        WrappedThrowable(Throwable cause) { super(cause); }
    }
}
