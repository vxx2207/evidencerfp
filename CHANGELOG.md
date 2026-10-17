# Changelog

Alle nennenswerten Änderungen an EvidenceRFP werden in dieser Datei dokumentiert. Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/); das Projekt verwendet semantische Versionierung.

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

