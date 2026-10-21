# Release-Checkliste v0.2.1

`v0.2.0` bleibt unverändert veröffentlicht. Diese Liste trennt die lokal vorbereiteten Patch-Metadaten von den Prüfungen und Veröffentlichungsschritten für `v0.2.1`.

## Patch lokal vorbereiten

- [x] historische Modell-, Prompt- und Retrieval-Provenienz persistieren
- [x] Analysejobs auf zwei Gesamtversuche und Provider-Wiederholungen auf höchstens zwei begrenzen
- [x] direkte Tests für Chat-Timeout, 429/5xx und fail-closed Modellausgaben ergänzen
- [x] Springdoc-Ausgabe gegen den versionierten OpenAPI-Vertrag absichern
- [x] optionales 80-Fälle-Ollama-/`bge-m3`-Evaluationsprofil dokumentieren
- [x] Backend, Frontend, Lockfile, OpenAPI und Springdoc auf `0.2.1` vereinheitlichen
- [x] `CHANGELOG.md` und `docs/releases/v0.2.1.md` ergänzen
- [x] Velat Isik, Wirtschaftsinformatik und Ziel Technical Sales / Presales im README sichtbar zuordnen
- [x] LinkedIn-Link und private Kontaktdaten bewusst nicht ergänzen

## Vollständige Verifikation

- [x] Backend mit `clean verify` prüfen; lokal ausschließlich Docker-Tests kontrolliert überspringen
- [x] GitHub CI mit allen 19 PostgreSQL-/pgvector-/Recovery-Tests ohne Skip abschließen
- [x] OpenAPI-Gate gegen die echte Ausgabe von `/v3/api-docs` prüfen
- [x] OpenAI-Profil aus einem sauberen Build kompilieren
- [x] optionales `ollama-eval`-Profil ohne Opt-in kompilieren und genau einen kontrollierten Skip bestätigen
- [x] Frontend mit `npm ci`, Produktions-Audit, Vitest, TypeScript/Vite und Playwright prüfen
- [x] OpenAPI-Client neu generieren und einen diff-freien Zustand bestätigen
- [x] Docker-Compose-Konfiguration und vollständigen Git-Diff prüfen

## Veröffentlichung

- [x] technische Fixes und Release-Metadaten getrennt committen
- [x] `main` pushen und vollständigen GitHub-CI-Lauf abwarten
- [x] annotierten Tag `v0.2.1` auf den grünen Commit setzen und pushen
- [x] automatisch erzeugtes GitHub Release samt sechs Demo-Artefakten prüfen
- [x] GitHub Pages, Live-Demo, Badges, Social Preview und Release-Link kontrollieren
- [x] sauberen Arbeitsbaum sowie identisches lokales und entferntes `main` bestätigen
- [x] bestätigen, dass Tag und Release `v0.2.0` unverändert geblieben sind
