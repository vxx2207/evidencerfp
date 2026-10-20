# Zu EvidenceRFP beitragen

Danke für dein Interesse. Dieses Repository ist ein deutschsprachiges Portfolio-Projekt mit bewusst engem MVP-Umfang. Beiträge sollen die Nachvollziehbarkeit, Sicherheit oder Demo-Qualität verbessern.

## Grundregeln

- Keine echten Kunden-, Personen-, Zugangsdaten oder vertraulichen Dokumente committen.
- Fiktive Namen, Werte und Ergebnisse deutlich als synthetisch markieren.
- Eine KI-Antwort darf nie ohne serverseitig validierte Zitation als `SUPPORTED` gelten.
- Deterministische IAM-Regeln bleiben unabhängig vom Sprachmodell.
- Änderungen an öffentlicher Demo und lokaler API müssen dasselbe Frontend-Datenmodell einhalten.
- Keine OCR-, Login-, Multi-Tenant-, CRM- oder Auto-Remediation-Funktion nebenbei in einen Bugfix aufnehmen.

## Lokale Entwicklung

```bash
git clone <repository-url>
cd evidence-rfp
cp .env.example .env
docker compose up -d postgres
```

Backend ohne echtes Modell testen:

```bash
cd backend
./mvnw verify
./mvnw spring-boot:run
```

Bei Änderungen an Controller, API-DTOs oder Fehlerantworten muss zusätzlich
`docs/openapi/evidence-rfp-v1.yaml` bewusst aktualisiert werden. `OpenApiContractTest` vergleicht
die echte `/v3/api-docs`-Ausgabe mit diesem Vertrag und lässt den Backend-CI-Job bei Drift scheitern.

Frontend testen:

```bash
cd frontend
npm ci
npm test
npm run build
```

Unter Windows `mvnw.cmd` statt `./mvnw` verwenden. Die vollständige lokale KI-Konfiguration steht im Root-[README](README.md).

## Branch und Pull Request

1. Einen kleinen, thematisch eindeutigen Branch anlegen.
2. Tests für geändertes Verhalten ergänzen.
3. OpenAPI-Vertrag, Dokumentation und Beispiel-Fixtures bei Schnittstellenänderungen aktualisieren.
4. Vor dem Push Backend-Tests, Frontend-Tests und Build ausführen.
5. Im Pull Request Problem, Lösung, Testnachweis und Sicherheitsauswirkung beschreiben.

Empfohlene Commit-Präfixe sind `feat:`, `fix:`, `test:`, `docs:`, `refactor:` und `chore:`.

## Definition of Done

- Das Verhalten ist durch Tests oder eine nachvollziehbare manuelle Prüfung belegt.
- Keine Geheimnisse, Build-Artefakte oder generierten Binär-Fixtures sind enthalten.
- Deutsche UI-Texte sind verständlich und konsistent.
- Fehlerzustände sind sichtbar; fehlende Evidenz wird nicht kaschiert.
- Öffentliche APIs und Umgebungsvariablen sind dokumentiert.
- Neue Quellen besitzen stabile Dokument-ID, Version, Gültigkeit und Abschnittsanker.

## Beispieldaten ändern

Die Markdown- und JSON-Dateien unter `examples/` sind die Source of Truth. Generierte PDF-, DOCX- und XLSX-Dateien bleiben unversioniert. Nach einer Änderung den `SampleDataGenerator` wie in [scripts/README.md](scripts/README.md) ausführen und anschließend Parser-, Retrieval- und Golden-Set-Tests starten.

## Security

Bitte keine vermutete Sicherheitslücke in einem öffentlichen Issue mit Exploit-Details veröffentlichen. Der sichere Meldeweg steht in [SECURITY.md](SECURITY.md).
