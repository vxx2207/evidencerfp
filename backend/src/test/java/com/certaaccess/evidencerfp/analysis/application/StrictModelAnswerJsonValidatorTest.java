package com.certaaccess.evidencerfp.analysis.application;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrictModelAnswerJsonValidatorTest {
    private final StrictModelAnswerJsonValidator validator =
            new StrictModelAnswerJsonValidator(new ObjectMapper());

    @Test
    void acceptsOnlyTheExactBoundedClaimSchema() {
        String id = UUID.randomUUID().toString();
        assertThatCode(() -> validator.validate("""
                {"claims":[{"text":"MFA ist aktiv.","citations":[{"chunkId":"%s",
                "quote":"Phishing-resistente MFA ist für Administratoren aktiv."}]}],
                "internalClarification":null}
                """.formatted(id))).doesNotThrowAnyException();
    }

    @Test
    void rejectsManipulatedJsonWithExtraFieldsMissingCitationsOrInventedIds() {
        String id = UUID.randomUUID().toString();
        assertThatThrownBy(() -> validator.validate("""
                {"claims":[{"text":"MFA ist aktiv.","citations":[{"chunkId":"%s",
                "quote":"MFA ist aktiv."}]}],"internalClarification":null,"status":"SUPPORTED"}
                """.formatted(id))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(
                "{\"claims\":[{\"text\":\"MFA ist aktiv.\",\"citations\":[]}],"
                        + "\"internalClarification\":null}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("""
                {"claims":[{"text":"MFA ist aktiv.","citations":[{"chunkId":"not-a-uuid",
                "quote":"MFA ist aktiv."}]}],"internalClarification":null}
                """))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnboundedProviderOutputBeforeSchemaBinding() {
        assertThatThrownBy(() -> validator.validate(" ".repeat(600_001)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
