---
documentId: CA-NHI-2026
title: CertaAccess Service Accounts und Secrets
version: "2.4"
publishedAt: 2026-07-01
validUntil: 2027-06-30
classification: SYNTHETISCHE-DEMOQUELLE
---

# CertaAccess Service Accounts und Secrets

> **SYNTHETISCHE DEMOQUELLE:** CertaAccess ist fiktiv. Dieses Dokument beschreibt keine reale Secret-Management- oder Workload-Identity-Lösung.

## Bevorzugte Authentifizierung {#workload-identity}

Für unterstützte Plattformen empfiehlt CertaAccess kurzlebige Workload-Identitäten über OIDC-Föderation. Dabei wird kein dauerhaftes Client Secret gespeichert. Ausgestellte Tokens besitzen eine maximale Laufzeit von 60 Minuten.

## Statische Zugangsdaten {#static-credentials}

Wenn eine Integration statische Zugangsdaten benötigt, müssen diese einem benannten technischen Owner und einem fachlichen Service zugeordnet sein. Das Secret wird nur bei der Erstellung einmalig angezeigt und anschließend nicht wieder im Klartext ausgegeben.

## Rotation {#rotation}

Statische Service-Account-Zugangsdaten müssen spätestens alle 90 Tage rotiert werden. Der Kontrolllauf vergleicht das Stichtagsdatum mit `credentialLastRotatedAt`. Ein Alter von mehr als 90 vollen Tagen erzeugt das Finding `IAM-003` mit hoher Priorität.

Eine automatische Rotation ist nur für explizit angebundene Zielsysteme möglich. In der Portfolio-Demo wird ausschließlich ein Snapshot geprüft und keine Zugangsinformation geändert.

## Schutz gespeicherter Secrets {#secret-protection}

Gespeicherte Secrets werden mit AES-256-GCM verschlüsselt. Der zugehörige Schlüssel wird getrennt von den verschlüsselten Daten verwaltet. Jeder Lese- und Rotationsvorgang erzeugt ein Audit-Ereignis.

## Deaktivierung {#disablement}

Service Accounts ohne aktiven Owner oder ohne beobachtete Nutzung über 180 Tage werden zur Prüfung markiert. Eine Deaktivierung erfolgt erst nach Bestätigung durch den Service Owner.

## Dokumentverantwortung {#ownership}

Fiktiver Owner: CertaAccess Non-Human Identity Team. Letzte synthetische Überprüfung: 1. Juli 2026.
