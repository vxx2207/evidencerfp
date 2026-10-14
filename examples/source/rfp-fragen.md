# Nordstern Bank – Identity Security RFP

> **SYNTHETISCHER DEMO-FRAGEBOGEN:** Nordstern Bank und CertaAccess sind fiktiv. Die Fragen enthalten keine echten Kundenanforderungen oder vertraulichen Informationen.

Der `SampleDataGenerator` liest die folgende Tabelle und erzeugt daraus `rfp-security-questionnaire.xlsx`. Spaltennamen im XLSX: `ID`, `Category`, `Question`.

| ID | Kategorie | Frage |
|---|---|---|
| RFP-001 | Verschlüsselung | Wie werden persistente Kundeninhalte im Ruhezustand verschlüsselt? |
| RFP-002 | Transport Security | Welche TLS-Versionen werden für Browser und APIs unterstützt? |
| RFP-003 | Datenresidenz | Können europäische Kundendaten in Frankfurt gespeichert werden? |
| RFP-004 | Verfügbarkeit | Welche monatliche Verfügbarkeit wird im Demo-Szenario zugesagt? |
| RFP-005 | Integration | Welche Standards unterstützt CertaAccess für SSO und Provisionierung? |
| RFP-006 | MFA | Dürfen privilegierte Konten TOTP als einzigen zweiten Faktor verwenden? |
| RFP-007 | MFA | Welche Authentifizierungsmethoden gelten als phishing-resistent? |
| RFP-008 | Account Recovery | Wie wird ein privilegiertes Konto wiederhergestellt? |
| RFP-009 | Passwörter | Wie werden lokale Passwörter gespeichert? |
| RFP-010 | Privileged Access | Wie lange ist ein genehmigter Just-in-Time-Zugriff standardmäßig aktiv? |
| RFP-011 | Privileged Access | Welches Genehmigungsverfahren gilt für kritische privilegierte Rollen? |
| RFP-012 | Lifecycle | Wie schnell werden über SCIM gemeldete Deaktivierungen verarbeitet? |
| RFP-013 | Service Accounts | In welchem Intervall müssen statische Service-Account-Zugangsdaten rotiert werden? |
| RFP-014 | Service Accounts | Welche Authentifizierung wird für Workloads bevorzugt? |
| RFP-015 | Secrets | Wie werden gespeicherte Secrets geschützt? |
| RFP-016 | Audit | Wie lange werden Audit-Ereignisse standardmäßig aufbewahrt? |
| RFP-017 | Monitoring | Wie lassen sich Audit-Ereignisse an ein SIEM exportieren? |
| RFP-018 | Datenschutz | Verarbeitet CertaAccess biometrische Rohdaten und wie lange werden diese gespeichert? |
| RFP-019 | Versicherung | Wie hoch ist die Cyberversicherungssumme des Anbieters? |
| RFP-020 | Kryptografie | Welche quantenresistenten Algorithmen sind bereits produktiv aktiviert? |
| RFP-021 | IAM Kontrollnachweis | Gibt es im bereitgestellten Snapshot ein privilegiertes Konto ohne phishing-resistente MFA? |
| RFP-022 | IAM Kontrollnachweis | Gibt es im bereitgestellten Snapshot ein verwaistes privilegiertes Konto? |
| RFP-023 | IAM Kontrollnachweis | Gibt es Service-Account-Zugangsdaten, die älter als 90 Tage sind? |
| RFP-024 | Rechtliche Bewertung | Ist der exportierte Nachweis rechtlich als Compliance-Zertifikat anerkannt? |
