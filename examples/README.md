# Beispieldaten

> Sämtliche Inhalte in diesem Verzeichnis sind **synthetisch**. CertaAccess und Nordstern Bank sind fiktiv. Die Dateien enthalten keine echten Personen, Zugangsdaten, Kundeninformationen oder Herstellerzusagen.

## Inhalt

| Pfad | Zweck |
|---|---|
| `source/*.md` | Lesbare Ursprungsquellen für fünf fiktive Produktdokumente |
| `source/rfp-fragen.md` | Lesbare Source of Truth für den 24-Fragen-RFP |
| `identity-snapshot.json` | Synthetischer IAM-Tenant-Snapshot mit positiven und negativen Regelfällen |
| `golden-set.json` | 20 deutsche Retrieval-Fragen mit erwarteten Quellen und Statuswerten |
| `rfp-questions.json` | Maschinenlesbare Spiegelung der 24 Demo-Fragen |
| `generated/` | Lokal erzeugte PDF-, DOCX- und XLSX-Fixtures; nicht versioniert |

Die Markdown-Dateien sind die nachvollziehbare Source of Truth. Binärdateien werden nicht ins Repository eingecheckt. Der Backend-`SampleDataGenerator` erzeugt lokal die textbasierten PDF-/DOCX-Dokumente und das XLSX aus diesen Quellen. Der konkrete Aufruf steht in [scripts/README.md](../scripts/README.md). Bei Änderungen müssen `source/rfp-fragen.md` und `rfp-questions.json` dieselben IDs, Kategorien und Fragen enthalten.

## Absichtlich eingebaute Fälle

- `CA-PLATFORM-2025` beschreibt für Version 3.8 TOTP oder WebAuthn für Administratoren; `CA-AUTH-2026` verlangt in Version 4.2 phishing-resistente MFA. Die UI soll beide Passagen zeigen und eine Freigabe bis zur Quellenentscheidung blockieren.
- `CA-PLATFORM-2025` nennt 365 Tage Audit-Aufbewahrung; `CA-SECOPS-2026` nennt aktuell 400 Tage. Die ältere Passage ist ausdrücklich als historisch markiert.
- Drei Fragen im Golden Set sind nicht durch den Korpus beantwortbar und müssen `NOT_FOUND` ergeben.
- Der IAM-Snapshot enthält je einen erwarteten Treffer pro Regel und zusätzliche Objekte, die keinen Treffer erzeugen dürfen.

## Golden-Set-Auswertung

`answerable: true` bedeutet, dass mindestens eine erwartete Quelle existiert. Für diese Fragen gilt:

```text
Recall@6 = Fragen mit mindestens einer erwarteten Quelle in den Top 6
           ÷ alle beantwortbaren Fragen
Ziel: Recall@6 >= 0,90
```

Der versionierte JUnit-Lauf erreicht im deterministischen Offline-Retriever aktuell **94,1 % (16/17)** und schlägt unter 90 % fehl. Zusätzlich prüft er, dass alle drei unbeantwortbaren Golden-Set-Fragen ohne Zitation `NOT_FOUND` ergeben.

Bei `PARTIAL` müssen sämtliche Einträge in `expectedSources` im Review sichtbar sein, bevor der Konflikt fachlich aufgelöst wird. Für `NOT_FOUND` darf keine Antwort als belegt erscheinen.

## Deterministische IAM-Erwartung

Stichtag des Snapshots ist der 1. Oktober 2026. Erwartet werden genau diese Findings:

| Regel | Objekt | Grund |
|---|---|---|
| `IAM-001` | `acc-mara-admin` | privilegiert, aber nur TOTP |
| `IAM-002` | `acc-legacy-admin` | kein Owner hinterlegt |
| `IAM-003` | `svc-reporting` | Secret älter als 90 volle Tage |

`acc-lena-admin`, `acc-breakglass`, `svc-payments` und das deaktivierte `svc-federated-ci` sind Negativkontrollen. Das Break-glass-Konto besitzt einen Owner und phishing-resistente MFA; das deaktivierte föderierte CI-Konto wird von der statischen Credential-Prüfung übersprungen.

## Nutzungshinweis

Die Dateien sind Test- und Demo-Eingaben, keine Compliance-Nachweise. Vor einer Erweiterung sollten neue Quellen eine stabile `documentId`, Version, Veröffentlichungs- und Gültigkeitsdatum sowie eindeutige Abschnittsanker erhalten.
