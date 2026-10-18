package com.certaaccess.evidencerfp.evidence;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserExecutionTest {
    @Test
    void cancelsWorkAfterConfiguredTimeout() {
        assertThatThrownBy(() -> ParserExecution.within(Duration.ofMillis(10), () -> {
            Thread.sleep(Duration.ofSeconds(2));
            return "zu spät";
        })).isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("Verarbeitungszeitlimit");
    }
}
