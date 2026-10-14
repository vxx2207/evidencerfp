package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.Analysis;
import com.certaaccess.evidencerfp.analysis.domain.AnalysisStatus;
import com.certaaccess.evidencerfp.analysis.domain.AnswerStatus;
import com.certaaccess.evidencerfp.analysis.domain.Citation;
import com.certaaccess.evidencerfp.analysis.domain.EvidenceDocument;
import com.certaaccess.evidencerfp.analysis.domain.Finding;
import com.certaaccess.evidencerfp.analysis.domain.FindingSeverity;
import com.certaaccess.evidencerfp.analysis.domain.FindingType;
import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.analysis.domain.ReviewStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class DemoAnalysisFactory {
    public static final UUID DEMO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant CREATED_AT = Instant.parse("2026-10-13T08:00:00Z");

    public Analysis create() {
        Finding conflict = Finding.of(FindingType.CONFLICTING_EVIDENCE, FindingSeverity.HIGH, "EVIDENCE-002",
                "Widersprüchliche Aufbewahrungsfristen",
                "Produktübersicht und Betriebsleitfaden nennen unterschiedliche Fristen.",
                "Audit-Logs", "Die gültige Frist durch Product Management bestätigen lassen.", true);
        Finding stale = Finding.of(FindingType.STALE_DOCUMENT, FindingSeverity.WARNING, "EVIDENCE-003",
                "Veralteter Nachweis",
                "Der referenzierte Notfallleitfaden ist älter als zwölf Monate.",
                "04-notfallmanagement.docx", "Eine aktuelle, freigegebene Version anfordern.", true);
        Finding missing = Finding.of(FindingType.MISSING_EVIDENCE, FindingSeverity.WARNING, "EVIDENCE-001",
                "Kein belastbarer Beleg",
                "In den bereitgestellten Unterlagen wurde keine belastbare Aussage gefunden.",
                "Frage RFP-018", "Verbindliche Aussage bei Legal und Product Management anfragen.", true);
        List<Finding> iam = List.of(
                Finding.of(FindingType.IAM_CONTROL_FAILED, FindingSeverity.HIGH, "IAM-001",
                        "Privilegiertes Konto ohne phishing-resistente MFA",
                        "Das synthetische Administratorkonto nutzt nur TOTP.", "adm-lena-demo",
                        "FIDO2-Authentifizierung verpflichtend aktivieren.", true),
                Finding.of(FindingType.IAM_CONTROL_FAILED, FindingSeverity.HIGH, "IAM-002",
                        "Verwaistes privilegiertes Konto",
                        "Für das synthetische Break-Glass-Konto ist kein Owner hinterlegt.", "adm-breakglass-demo",
                        "Owner zuweisen und Nutzung überwachen.", true),
                Finding.of(FindingType.IAM_CONTROL_FAILED, FindingSeverity.HIGH, "IAM-003",
                        "Veraltete Service-Account-Zugangsdaten",
                        "Das synthetische ERP-Servicekonto wurde seit 127 Tagen nicht rotiert.", "svc-erp-demo",
                        "Credential rotieren und automatische Rotation einrichten.", true));

        List<QuestionResult> questions = new ArrayList<>();
        questions.add(supported("RFP-001", "Identität", "Unterstützt die Lösung Single Sign-on über SAML 2.0?",
                "Ja. CertaAccess unterstützt SAML 2.0 und OpenID Connect für föderiertes Single Sign-on.",
                "01-produktuebersicht.pdf", "Seite 3"));
        questions.add(supported("RFP-002", "Identität", "Werden FIDO2 und Passkeys unterstützt?",
                "Ja. Phishing-resistente Anmeldung ist über FIDO2, WebAuthn und Passkeys verfügbar.",
                "02-authentifizierung.docx", "Abschnitt 2.1"));
        questions.add(supported("RFP-003", "Identität", "Kann MFA für privilegierte Rollen erzwungen werden?",
                "Richtlinien können phishing-resistente MFA für privilegierte Rollen verpflichtend erzwingen.",
                "02-authentifizierung.docx", "Abschnitt 3.2"));
        questions.add(supported("RFP-004", "Provisionierung", "Ist SCIM 2.0 verfügbar?",
                "SCIM 2.0 wird für automatisierte Bereitstellung und Deprovisionierung unterstützt.",
                "01-produktuebersicht.pdf", "Seite 5"));
        questions.add(supported("RFP-005", "Provisionierung", "Wie schnell werden gesperrte Benutzer deaktiviert?",
                "Deprovisionierungsereignisse werden im Regelfall innerhalb von fünf Minuten verarbeitet.",
                "03-betriebsleitfaden.pdf", "Seite 8"));
        questions.add(supported("RFP-006", "Berechtigungen", "Gibt es rollenbasierte Zugriffskontrolle?",
                "CertaAccess unterstützt rollenbasierte Zugriffskontrolle und zeitlich begrenzte Zuweisungen.",
                "01-produktuebersicht.pdf", "Seite 6"));
        questions.add(supported("RFP-007", "Berechtigungen", "Werden Rezertifizierungskampagnen unterstützt?",
                "Periodische und ereignisbasierte Rezertifizierungskampagnen sind konfigurierbar.",
                "05-governance.docx", "Abschnitt 4"));
        questions.add(supported("RFP-008", "Privileged Access", "Werden privilegierte Sitzungen protokolliert?",
                "Privilegierte Sitzungsereignisse werden manipulationsgeschützt protokolliert.",
                "03-betriebsleitfaden.pdf", "Seite 12"));
        questions.add(supported("RFP-009", "Audit", "Können Logs an ein SIEM exportiert werden?",
                "Audit-Ereignisse lassen sich per Syslog oder API an ein SIEM übertragen.",
                "03-betriebsleitfaden.pdf", "Seite 14"));
        questions.add(new QuestionResult("RFP-010", "Wie lange werden Audit-Logs aufbewahrt?", "Audit",
                "Eine Freigabe ist nicht möglich: Die Unterlagen nennen 180 und 365 Tage.", AnswerStatus.PARTIAL,
                ReviewStatus.CHANGES_REQUIRED,
                List.of(citation("01-produktuebersicht.pdf", "Seite 7", "Audit-Logs werden 180 Tage vorgehalten."),
                        citation("03-betriebsleitfaden.pdf", "Seite 15", "Audit-Logs werden 365 Tage vorgehalten.")),
                "Welche Aufbewahrungsfrist ist vertraglich verbindlich?", List.of(conflict)));
        questions.add(supported("RFP-011", "Verschlüsselung", "Werden Daten bei der Übertragung verschlüsselt?",
                "Alle externen Verbindungen verwenden mindestens TLS 1.2; TLS 1.3 wird bevorzugt.",
                "01-produktuebersicht.pdf", "Seite 9"));
        questions.add(supported("RFP-012", "Verschlüsselung", "Wie werden ruhende Daten geschützt?",
                "Ruhende Mandantendaten werden mit AES-256 verschlüsselt.",
                "01-produktuebersicht.pdf", "Seite 9"));
        questions.add(supported("RFP-013", "Betrieb", "Ist Hochverfügbarkeit vorgesehen?",
                "Der Dienst wird redundant über drei Verfügbarkeitszonen betrieben.",
                "03-betriebsleitfaden.pdf", "Seite 4"));
        questions.add(supported("RFP-014", "Betrieb", "Welche Wiederanlaufzeit wird angestrebt?",
                "Für den Kerndienst ist ein RTO von vier Stunden dokumentiert.",
                "04-notfallmanagement.docx", "Abschnitt 3"));
        questions.add(new QuestionResult("RFP-015", "Wie oft werden Notfalltests durchgeführt?", "Betrieb",
                "Laut Notfallleitfaden findet jährlich ein Wiederherstellungstest statt.", AnswerStatus.PARTIAL,
                ReviewStatus.CHANGES_REQUIRED,
                List.of(citation("04-notfallmanagement.docx", "Abschnitt 5",
                        "Ein vollständiger Wiederherstellungstest findet jährlich statt.")),
                "Liegt ein aktueller Testbericht vor?", List.of(stale)));
        questions.add(supported("RFP-016", "Datenschutz", "Ist eine Löschung nach Vertragsende vorgesehen?",
                "Mandantendaten werden nach Vertragsende innerhalb von 30 Tagen gelöscht.",
                "03-betriebsleitfaden.pdf", "Seite 18"));
        questions.add(supported("RFP-017", "Datenschutz", "Werden Daten in der EU verarbeitet?",
                "Die synthetische Standardbereitstellung verarbeitet Kundendaten ausschließlich in EU-Regionen.",
                "01-produktuebersicht.pdf", "Seite 11"));
        questions.add(new QuestionResult("RFP-018", "Besteht eine branchenspezifische Zertifizierung für Banken?",
                "Compliance", "Compliance-Unterlagen enthalten dazu keine belastbare Aussage.", AnswerStatus.NOT_FOUND,
                ReviewStatus.DRAFT, List.of(),
                "Welche konkrete Zertifizierung und welcher Geltungsbereich werden verlangt?", List.of(missing)));
        questions.add(supported("RFP-019", "Governance", "Gibt es ein Vier-Augen-Prinzip für kritische Rollen?",
                "Kritische Rollenzuweisungen können einen zweistufigen Genehmigungsworkflow verlangen.",
                "05-governance.docx", "Abschnitt 5"));
        questions.add(supported("RFP-020", "Governance", "Können Funktionstrennungskonflikte erkannt werden?",
                "Konfigurierbare SoD-Regeln erkennen unvereinbare Rollen bereits im Antragsprozess.",
                "05-governance.docx", "Abschnitt 6"));
        questions.add(supported("RFP-021", "API", "Ist eine dokumentierte REST-API vorhanden?",
                "Eine versionierte REST-API steht für Identitäten, Rollen und Audit-Ereignisse bereit.",
                "01-produktuebersicht.pdf", "Seite 13"));
        questions.add(supported("RFP-022", "Monitoring", "Wie werden Sicherheitsereignisse gemeldet?",
                "Kritische Sicherheitsereignisse erzeugen Webhooks und Benachrichtigungen an definierte Kontakte.",
                "03-betriebsleitfaden.pdf", "Seite 16"));
        questions.add(supported("RFP-023", "Support", "Gibt es einen deutschsprachigen Supportkanal?",
                "Für Enterprise-Verträge ist deutschsprachiger Support an Werktagen dokumentiert.",
                "03-betriebsleitfaden.pdf", "Seite 20"));
        questions.add(supported("RFP-024", "Onboarding", "Ist ein Proof of Concept mit Erfolgskriterien möglich?",
                "Ein vierwöchiger PoC kann mit vorab vereinbarten technischen Erfolgskriterien durchgeführt werden.",
                "01-produktuebersicht.pdf", "Seite 15"));

        List<EvidenceDocument> documents = List.of(
                new EvidenceDocument("01-produktuebersicht.pdf", "application/pdf", 15,
                        LocalDate.of(2026, 9, 30), false),
                new EvidenceDocument("02-authentifizierung.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 6,
                        LocalDate.of(2026, 9, 18), false),
                new EvidenceDocument("03-betriebsleitfaden.pdf", "application/pdf", 20,
                        LocalDate.of(2026, 9, 20), false),
                new EvidenceDocument("04-notfallmanagement.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 7,
                        LocalDate.of(2024, 4, 15), true),
                new EvidenceDocument("05-governance.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 8,
                        LocalDate.of(2026, 8, 29), false));
        List<Finding> findings = new ArrayList<>(List.of(conflict, stale, missing));
        findings.addAll(iam);
        return new Analysis(DEMO_ID, "Musterbank Nova (synthetisch)", true, AnalysisStatus.COMPLETED,
                CREATED_AT, CREATED_AT.plusSeconds(18), questions, findings, documents);
    }

    private QuestionResult supported(String id, String category, String question, String answer,
                                     String document, String locator) {
        return new QuestionResult(id, question, category, answer, AnswerStatus.SUPPORTED,
                ReviewStatus.DRAFT, List.of(citation(document, locator, answer)), null, List.of());
    }

    private Citation citation(String document, String locator, String quote) {
        UUID chunkId = UUID.nameUUIDFromBytes((document + locator).getBytes(StandardCharsets.UTF_8));
        return new Citation(chunkId, document, locator, quote);
    }
}
