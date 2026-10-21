package com.certaaccess.evidencerfp.analysis.infrastructure;

import com.certaaccess.evidencerfp.analysis.application.AnswerGenerationException;
import com.certaaccess.evidencerfp.analysis.application.ClaimVerifier;
import com.certaaccess.evidencerfp.analysis.application.EvidenceAnswerPolicy;
import com.certaaccess.evidencerfp.analysis.application.StrictModelAnswerJsonValidator;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.evidence.CitationVerifier;
import com.certaaccess.evidencerfp.evidence.EvidenceChunk;
import com.certaaccess.evidencerfp.evidence.RfpQuestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaAnswerGeneratorProviderTest {
    private static final UUID CHUNK_ID = UUID.fromString("2d8e44f3-e662-5f58-8f7a-071408f97e57");
    private static final String QUOTE =
            "Phishing-resistente MFA ist für privilegierte Konten verpflichtend.";
    private static final RfpQuestion QUESTION = new RfpQuestion(
            "IAM-001", "Ist phishing-resistente MFA verpflichtend?", "MFA");
    private static final List<EvidenceChunk> CHUNKS = List.of(new EvidenceChunk(
            CHUNK_ID, "mfa.pdf", "application/pdf", "Seite 3", QUOTE, null));

    @Test
    void stopsAChatAttemptAtTheConfiguredTimeout() {
        AtomicInteger calls = new AtomicInteger();
        var generator = generator((system, user) -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return validResponse();
        }, Duration.ofMillis(25), 0);
        long started = System.nanoTime();

        assertThatThrownBy(() -> generator.generate(QUESTION, CHUNKS))
                .isInstanceOfSatisfying(AnswerGenerationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("PROVIDER_RETRIES_EXHAUSTED"));

        assertThat(calls).hasValue(1);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP 429", "HTTP 503"})
    void retriesRetryableRateLimitAndServerFailures(String failure) {
        AtomicInteger calls = new AtomicInteger();
        var generator = generator((system, user) -> {
            if (calls.incrementAndGet() < 3) throw new IllegalStateException(failure);
            return validResponse();
        }, Duration.ofSeconds(1), 2);

        var answer = generator.generate(QUESTION, CHUNKS);

        assertThat(answer.answerStatus()).isEqualTo(AnswerStatus.SUPPORTED);
        assertThat(calls).hasValue(3);
    }

    @Test
    void capsConfiguredChatRetriesAtTwo() {
        AtomicInteger calls = new AtomicInteger();
        var generator = generator((system, user) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("HTTP 503");
        }, Duration.ofSeconds(1), 99);

        assertThatThrownBy(() -> generator.generate(QUESTION, CHUNKS))
                .isInstanceOfSatisfying(AnswerGenerationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("PROVIDER_RETRIES_EXHAUSTED"));
        assertThat(calls).hasValue(3);
    }

    @Test
    void doesNotRetryInvalidModelOutput() {
        AtomicInteger calls = new AtomicInteger();
        var generator = generator((system, user) -> {
            calls.incrementAndGet();
            return "{\"claims\":\"manipuliert\"}";
        }, Duration.ofSeconds(1), 2);

        assertThatThrownBy(() -> generator.generate(QUESTION, CHUNKS))
                .isInstanceOfSatisfying(AnswerGenerationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_MODEL_OUTPUT"));
        assertThat(calls).hasValue(1);
    }

    private OllamaAnswerGenerator generator(OllamaAnswerGenerator.ChatInvocation invocation,
                                            Duration timeout, int maxRetries) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new OllamaAnswerGenerator(invocation, objectMapper,
                new ClaimVerifier(new CitationVerifier()),
                new EvidenceAnswerPolicy(
                        Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC), 365),
                new StrictModelAnswerJsonValidator(objectMapper), timeout, maxRetries);
    }

    private static String validResponse() {
        return """
                {"claims":[{"text":"Phishing-resistente MFA ist verpflichtend.",
                "citations":[{"chunkId":"%s","quote":"%s"}]}],
                "internalClarification":null}
                """.formatted(CHUNK_ID, QUOTE);
    }
}
