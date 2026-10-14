package com.certaaccess.evidencerfp.iam;

import com.certaaccess.evidencerfp.analysis.domain.Finding;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IamControlEvaluatorTest {
    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
    private final IamControlEvaluator evaluator = new IamControlEvaluator(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void reportsAllThreeControlFailuresDeterministically() {
        IamSnapshot snapshot = new IamSnapshot(NOW,
                List.of(new IamAccount("adm-1", "Demo Admin", true, true,
                        List.of("totp"), null)),
                List.of(new ServiceAccount("svc-1", "Demo ERP", true,
                        NOW.minusSeconds(91L * 24 * 60 * 60), "owner-1")));

        List<Finding> findings = evaluator.evaluate(snapshot);

        assertThat(findings).extracting(Finding::ruleId)
                .containsExactlyInAnyOrder("IAM-001", "IAM-002", "IAM-003");
        assertThat(findings).allMatch(Finding::blocksApproval);
    }

    @Test
    void acceptsCompliantAccountsAndBoundaryAtNinetyDays() {
        IamSnapshot snapshot = new IamSnapshot(NOW,
                List.of(new IamAccount("adm-2", "Demo Admin", true, true,
                        List.of("FIDO2 security key"), "owner-2")),
                List.of(new ServiceAccount("svc-2", "Demo API", true,
                        NOW.minusSeconds(90L * 24 * 60 * 60), "owner-2")));

        assertThat(evaluator.evaluate(snapshot)).isEmpty();
    }

    @Test
    void ignoresDisabledAccounts() {
        IamSnapshot snapshot = new IamSnapshot(NOW,
                List.of(new IamAccount("adm-off", "Deaktiviert", true, false, List.of(), null)),
                List.of(new ServiceAccount("svc-off", "Deaktiviert", false, null, null)));

        assertThat(evaluator.evaluate(snapshot)).isEmpty();
    }
}
