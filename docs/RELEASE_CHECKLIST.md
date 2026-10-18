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
- [ ] XLSX-Download zusätzlich in Excel oder LibreOffice öffnen
- [ ] Screenshots und Demo-Aufnahme aktualisieren
- [x] Versionsnummern auf `0.2.0` vereinheitlichen
- [x] Arbeitsbaum sauber committen

## Nach Bereitstellung des GitHub-Remotes

- [ ] Remote `origin` setzen und `main` pushen
- [ ] Repository-Beschreibung, Topics und `docs/assets/social-preview.png` als Social Preview setzen
- [ ] GitHub Pages auf „GitHub Actions“ stellen und Deployment prüfen
- [ ] echten Live-Demo-Link in README und Repository-Metadaten eintragen
- [ ] Badge-URLs auf den tatsächlichen Owner-/Repository-Pfad umstellen
- [ ] Changelog-Link `OWNER/evidence-rfp` ersetzen
- [ ] `main` und annotierten Tag `v0.2.0` pushen
- [ ] GitHub Release mit Demo-Link, Screenshots und synthetischem Beispiel-Evidence-Pack veröffentlichen

## Persönliches Portfolio

- [ ] Name, Studiengang und Zielrolle ergänzen
- [ ] optional LinkedIn- oder Portfolio-Link ergänzen
- [ ] keine privaten Kontaktdaten oder echten Kundendaten veröffentlichen
