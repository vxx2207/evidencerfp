# Release-Checkliste v0.2.0

Diese Liste trennt lokal vollständig vorbereitete Arbeit von Schritten, die erst nach Verbindung mit einem persönlichen GitHub-Repository möglich sind.

## Lokal

- [x] Backend-, Frontend- und Browsertests lokal grün; Docker-Tests werden ohne Docker kontrolliert übersprungen
- [x] Ollama- und OpenAI-Profile kompilieren
- [x] GitHub-Pages-Artefakt lokal bauen
- [x] 60-Sekunden-Tour, Deep Links, Reset und XLSX-Download per Playwright prüfen
- [x] Browser-XLSX technisch und visuell prüfen: vier Blätter, lesbare Kopfzeilen, keine Formeln oder Formel-Fehler
- [x] OpenAPI-Client neu generieren und Vertrag auf Drift prüfen
- [x] deterministischen 80-Fälle-Qualitätsbericht erzeugen
- [ ] Testcontainers-Suite mit Docker beziehungsweise in GitHub Actions ausführen
- [x] XLSX-Download schreibgeschützt in Microsoft Excel 16.0 öffnen und Struktur/Stile prüfen
- [x] geprüftes synthetisches Beispiel-Evidence-Pack im Repository bereitstellen
- [x] CI so konfigurieren, dass übersprungene Testcontainers-Tests den GitHub-Lauf fehlschlagen lassen
- [x] taggesteuerten GitHub-Release mit Demo-Artefakten vorbereiten
- [x] Screenshots für Quellenpassage, Konflikt und IAM-Finding aktualisieren
- [x] kurze animierte Tour im README ergänzen
- [x] 90-Sekunden-Aufnahme in 1440 × 900 mit WebVTT-Untertiteln erstellen
- [x] Versionsnummern auf `0.2.0` vereinheitlichen
- [x] Arbeitsbaum sauber committen

## Nach Bereitstellung des GitHub-Remotes

- [x] Remote `origin` setzen und `main` pushen
- [x] Repository-Beschreibung, Homepage und Topics setzen
- [ ] optimiertes `docs/assets/social-preview.jpg` als Social Preview setzen
- [x] GitHub Pages auf „GitHub Actions“ stellen und Deployment prüfen
- [x] echten Live-Demo-Link in README und Repository-Metadaten eintragen
- [x] Badge-URLs auf den tatsächlichen Owner-/Repository-Pfad umstellen
- [ ] `main` und annotierten Tag `v0.2.0` pushen
- [ ] automatisch erzeugtes GitHub Release mit Demo-Artefakten prüfen

## Persönliches Portfolio

- [ ] Name, Studiengang und Zielrolle ergänzen
- [ ] optional LinkedIn- oder Portfolio-Link ergänzen
- [ ] keine privaten Kontaktdaten oder echten Kundendaten veröffentlichen
