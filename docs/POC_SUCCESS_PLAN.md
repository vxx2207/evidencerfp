# PoC Success Plan

## Ziel

In vier Wochen nachweisen, dass EvidenceRFP bei einem begrenzten, anonymisierten Datensatz Recherchezeit reduziert, ohne die Nachvollziehbarkeit oder Antwortqualität zu verschlechtern.

## Umfang

- 1 anonymisierter historischer Security-Fragebogen mit 50–100 Fragen
- 5–10 freigegebene, textbasierte Produktdokumente
- optional 1 synthetischer IAM-Snapshot im vereinbarten Schema
- 2 Sales Engineers und 1 Security Reviewer
- lokale Ausführung; keine Verbindung zu CRM, IdP oder Produktivsystemen

## Erfolgskriterien

| Dimension | Messgröße | Erfolgsschwelle | Messmethode |
|---|---|---:|---|
| Retrieval | erwartete Quelle in Top 6 | ≥ 90 % der beantwortbaren Fragen | kuratiertes Golden Set |
| Belegtreue | technisch gültige Zitationen | 100 % | serverseitige Prüfung + Stichprobe |
| Sicherheit | unbeantwortbare Fragen als `NOT_FOUND` | 100 % des Testsets | Negativfragen |
| Effizienz | aktive Bearbeitungszeit | ≥ 30 % geringer als Baseline | Zeitmessung pro Frage |
| Nutzbarkeit | akzeptierte Erstentwürfe | ≥ 60 % ohne komplette Neufassung | Review-Entscheidung |
| Kontrolle | Konflikte blockieren Freigabe | 100 % der Konflikt-Fixtures | automatisierter Test |
| IAM | erwartete Rule Findings | 100 % | positive und negative Snapshots |

Eine erfolgreiche Demo allein gilt nicht als PoC-Erfolg. Alle Muss-Kriterien müssen erfüllt oder mit einer dokumentierten, akzeptierten Abweichung versehen sein.

## Ablauf

### Woche 1 – Baseline und Daten

- Rollen, erlaubte Quellen und Antwortpolicy festlegen
- personenbezogene und vertrauliche Inhalte entfernen
- Baseline-Zeit und Golden Set erfassen

### Woche 2 – Konfiguration

- Dokumente importieren und Metadaten prüfen
- Retrieval und lokale Modelle konfigurieren
- Kontrollregeln gegen synthetischen IAM-Snapshot prüfen

### Woche 3 – Blindtest

- Fragebogen ohne manuelle Vorselektion verarbeiten
- Ergebnisse durch Sales Engineer und Security Reviewer bewerten
- Fehler nach Retrieval, Generierung, Quelle oder Prozess klassifizieren

### Woche 4 – Auswertung

- Metriken und qualitative Findings zusammenführen
- Sicherheits- und Betriebs-Gaps dokumentieren
- Go, Iterate oder Stop anhand der vorab vereinbarten Kriterien entscheiden

## Rollen

| Rolle | Verantwortung |
|---|---|
| Business Sponsor | Ziel, Umfang und Go/No-Go |
| Sales Engineer | Baseline und Nutzerreview |
| Security Reviewer | Quellenfreigabe und Qualitätsurteil |
| Technical Owner | Installation, Logs, Fehleranalyse |

## Abbruchkriterien

- Echte Kundendaten lassen sich nicht zuverlässig entfernen oder lokal halten.
- Das Team kann keine autoritativen Quellen benennen.
- Unbelegte Antworten werden wiederholt als belegt ausgegeben.
- Parser- oder Modellbetrieb verletzt vereinbarte Sicherheitsgrenzen.

## Ergebnisartefakte

Metrikbericht, Fehlerkatalog, freigegebenes Beispiel-Evidence-Pack, aktualisiertes Threat Model und eine Entscheidung mit klar benannten Voraussetzungen für den nächsten Schritt.
