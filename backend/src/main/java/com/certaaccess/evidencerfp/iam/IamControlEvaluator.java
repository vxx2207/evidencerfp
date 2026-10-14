package com.certaaccess.evidencerfp.iam;

import com.certaaccess.evidencerfp.analysis.domain.Finding;
import com.certaaccess.evidencerfp.analysis.domain.FindingSeverity;
import com.certaaccess.evidencerfp.analysis.domain.FindingType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class IamControlEvaluator {
    public static final String PHISHING_RESISTANT_MFA = "IAM-001";
    public static final String ORPHANED_PRIVILEGED_ACCOUNT = "IAM-002";
    public static final String STALE_SERVICE_CREDENTIAL = "IAM-003";
    private static final long MAX_CREDENTIAL_AGE_DAYS = 90;
    private static final Set<String> RESISTANT_METHODS = Set.of(
            "fido2", "passkey", "webauthn", "smartcard", "certificate", "hardware-key");

    private final Clock clock;

    public IamControlEvaluator(Clock clock) {
        this.clock = clock;
    }

    public List<Finding> evaluate(IamSnapshot snapshot) {
        if (snapshot == null) return List.of();
        Instant reference = snapshot.capturedAt() == null ? clock.instant() : snapshot.capturedAt();
        List<Finding> findings = new ArrayList<>();
        for (IamAccount account : snapshot.accounts()) {
            if (!account.enabled() || !account.privileged()) continue;
            if (!hasPhishingResistantMfa(account)) {
                findings.add(Finding.of(FindingType.IAM_CONTROL_FAILED, FindingSeverity.HIGH,
                        PHISHING_RESISTANT_MFA,
                        "Privilegiertes Konto ohne phishing-resistente MFA",
                        "Das aktivierte privilegierte Konto verwendet keine als phishing-resistent eingestufte Methode.",
                        display(account.id(), account.displayName()),
                        "FIDO2, Passkey, WebAuthn oder eine zertifikatsbasierte Methode verpflichtend aktivieren.",
                        true));
            }
            if (isBlank(account.ownerId())) {
                findings.add(Finding.of(FindingType.IAM_CONTROL_FAILED, FindingSeverity.HIGH,
                        ORPHANED_PRIVILEGED_ACCOUNT,
                        "Verwaistes privilegiertes Konto",
                        "Für das aktivierte privilegierte Konto ist kein verantwortlicher Owner hinterlegt.",
                        display(account.id(), account.displayName()),
                        "Einen verantwortlichen Owner zuweisen oder das Konto bis zur Klärung deaktivieren.",
                        true));
            }
        }
        for (ServiceAccount account : snapshot.serviceAccounts()) {
            if (!account.enabled()) continue;
            if (account.credentialLastRotatedAt() == null
                    || Duration.between(account.credentialLastRotatedAt(), reference).toDays() > MAX_CREDENTIAL_AGE_DAYS) {
                findings.add(Finding.of(FindingType.IAM_CONTROL_FAILED, FindingSeverity.HIGH,
                        STALE_SERVICE_CREDENTIAL,
                        "Veraltete Service-Account-Zugangsdaten",
                        account.credentialLastRotatedAt() == null
                                ? "Für den aktiven Service Account ist kein Rotationszeitpunkt dokumentiert."
                                : "Die Zugangsdaten des aktiven Service Accounts sind älter als 90 Tage.",
                        display(account.id(), account.displayName()),
                        "Zugangsdaten rotieren, Rotation automatisieren und einen Owner hinterlegen.",
                        true));
            }
        }
        return List.copyOf(findings);
    }

    public List<String> ruleDescriptions() {
        return List.of(
                PHISHING_RESISTANT_MFA + ": Privilegierte Konten benötigen phishing-resistente MFA",
                ORPHANED_PRIVILEGED_ACCOUNT + ": Privilegierte Konten benötigen einen Owner",
                STALE_SERVICE_CREDENTIAL + ": Service-Account-Zugangsdaten dürfen höchstens 90 Tage alt sein");
    }

    private boolean hasPhishingResistantMfa(IamAccount account) {
        return account.mfaMethods().stream()
                .filter(method -> method != null)
                .map(method -> method.toLowerCase(Locale.ROOT).replace('_', '-'))
                .anyMatch(method -> RESISTANT_METHODS.stream().anyMatch(method::contains));
    }

    private String display(String id, String name) {
        if (!isBlank(name) && !isBlank(id)) return name + " (" + id + ")";
        return isBlank(name) ? (isBlank(id) ? "Unbekanntes Konto" : id) : name;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
