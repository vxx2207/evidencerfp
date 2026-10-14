---
documentId: CA-SECOPS-2026
title: CertaAccess Security und Operations
version: "4.2"
publishedAt: 2026-09-01
validUntil: 2027-08-31
classification: SYNTHETISCHE-DEMOQUELLE
---

# CertaAccess Security und Operations

> **SYNTHETISCHE DEMOQUELLE:** Alle Betriebswerte, Kontakte, Reaktionszeiten und Architekturangaben sind für ein fiktives Verkaufsszenario erstellt.

## Audit Logging {#audit-logging}

Anmeldung, fehlgeschlagene Authentifizierung, Rollenaktivierung, Richtlinienänderung, Secret-Zugriff, Export und administrative API-Nutzung werden als strukturierte Audit-Ereignisse protokolliert. Ein Ereignis enthält Tenant-ID, UTC-Zeit, Actor, Aktion, Zielobjekt und Ergebnis.

## Aufbewahrung {#retention}

Seit Plattformversion 4.2 werden Audit-Ereignisse standardmäßig 400 Tage online vorgehalten. Diese aktuelle Aussage ersetzt den älteren Standardwert von 365 Tagen in Version 3.8. Ein unveränderlicher Langzeitexport liegt in der Verantwortung des Kunden.

EvidenceRFP soll bei einer allgemeinen Frage zur Aufbewahrung sowohl die alte als auch die aktuelle Fundstelle sichtbar machen, die ältere Quelle als veraltet markieren und eine bewusste Freigabe verlangen.

## SIEM-Export {#siem-export}

Audit-Ereignisse können fortlaufend per HTTPS-Webhooks oder gesammelt als JSON Lines exportiert werden. Fehlgeschlagene Webhook-Zustellungen werden mit exponentiellem Backoff bis zu 24 Stunden wiederholt. Exporte sind mandantenspezifisch signiert.

## Security Monitoring {#monitoring}

Das fiktive Security-Operations-Team überwacht Authentifizierungs- und Plattformalarme rund um die Uhr. Kritische Ereignisse werden nach einer synthetischen internen Zielzeit von 15 Minuten triagiert. Diese Zielzeit ist keine vertragliche Zusage.

## Incident Response {#incident-response}

Bestätigte Sicherheitsvorfälle werden eingedämmt, untersucht und dokumentiert. Betroffene Demo-Kunden würden ohne unangemessene Verzögerung über bestätigte Auswirkungen und verfügbare Gegenmaßnahmen informiert. Rechtliche Meldefristen werden kundenspezifisch bewertet und nicht durch dieses Dokument zugesagt.

## Wiederherstellung {#recovery}

Für die synthetische SaaS-Plattform gelten ein Recovery Time Objective von vier Stunden und ein Recovery Point Objective von einer Stunde. Beide Werte sind Demoannahmen und kein reales Service Level.

## Dokumentverantwortung {#ownership}

Fiktiver Owner: CertaAccess Security Operations. Letzte synthetische Überprüfung: 1. September 2026.
