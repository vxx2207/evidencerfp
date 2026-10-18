# Changelog

Alle nennenswerten Änderungen an EvidenceRFP werden in dieser Datei dokumentiert. Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/); das Projekt verwendet semantische Versionierung.

## [0.2.0] – 2026-10-18

### Hinzugefügt

- persistente Analysejobs mit PostgreSQL-Lease, Heartbeat, Recovery, Abbruchmarkierung und maximal zwei Versuchen
- idempotente Imports über `Idempotency-Key` sowie optimistische Review-Sperren über `If-Match`
- abschnitts- und satzorientiertes 450/60-Token-Chunking mit deterministischen Chunk-IDs
- Hybrid Retrieval aus pgvector und deutscher Volltextsuche mit Reciprocal Rank Fusion
- persistierte Claim- und Retrieval-Provenienz einschließlich Rängen, Verfahren, Hashes und Modellversionen
- versionierter 80-Fälle-Eval-Runner mit JSON- und HTML-Bericht
- stabiler OpenAPI-3.1-Vertrag und reproduzierbar generierter TypeScript-Client
- Micrometer-/Prometheus-Metriken, sichere Fehlercodes und strukturierte Korrelationslogs
- kompakter Techniknachweis in der Review-Oberfläche
- aktuelle Konflikt-/IAM-Screenshots, animierte Kurztour und 90-Sekunden-Aufnahme mit WebVTT-Untertiteln

### Geändert

- Uploads werden für die Joblaufzeit unter servergenerierten Namen gespeichert und nach terminalem Zustand entfernt
- Reviewänderung und append-only Audit-Ereignis werden atomar gespeichert; vollständiges Delete/Reinsert entfällt dort
- Ergebnis, Review und Export verlangen den Zustand `COMPLETED`
- Frontend und Backend verwenden einheitlich Version `0.2.0`

### Sicherheit

- PDF-, DOCX- und XLSX-Verarbeitung erzwingt Seiten-, Zeichen-, Chunk-, Dekompressions-, Verhältnis-, Eintrags- und Zeitbudgets
- OOXML mit Makros, OLE-Paketen, externen Beziehungen, Verschlüsselung, doppelten Einträgen oder Pfad-Traversal wird abgelehnt
- Claims werden fail-closed gegen erlaubte Chunk-IDs, exakte Zitate, Zahlen, zentrale Entitäten und Negationen geprüft; auch Größe und Felder der Modell-JSON sind hart begrenzt
- Provideraufrufe besitzen Timeouts und höchstens zwei Wiederholungen; technische Ausfälle werden nie als `NOT_FOUND` ausgegeben

## [0.1.0] – 2026-10-15

### Hinzugefügt

- geführte 60-Sekunden-Demo mit teilbaren Deep Links
- statischer und lokaler Analysemodus mit gemeinsamem Gateway-Modell
- echte XLSX Evidence Packs mit Antworten, IAM-Findings, Quellen und Reviewinformationen
- vollständige, serverseitig persistierte Quellenpassagen
- append-only Review-Protokoll mit Antwortprovenienz
- drei deterministische Identity-Security-Kontrollen
- GitHub-Pages-, CI-, Dependabot- und Playwright-Workflows
- synthetischer Golden-Set-Datensatz mit gemessenem Recall@6

### Sicherheit

- serverseitige Zitationsprüfung und Formula-Injection-Schutz
- Uploadprüfung über Endung, MIME-Type, Magic Bytes und Größenlimits
- lokaler Production-Stack standardmäßig an Loopback gebunden

