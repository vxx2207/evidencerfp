---
documentId: CA-PLATFORM-2025
title: CertaAccess Plattformüberblick
version: "3.8"
publishedAt: 2025-11-15
validUntil: 2026-11-14
classification: SYNTHETISCHE-DEMOQUELLE
---

# CertaAccess Plattformüberblick

> **SYNTHETISCHE DEMOQUELLE:** CertaAccess ist ein fiktives Unternehmen. Alle Produkteigenschaften, Kennzahlen und Zusagen in diesem Dokument sind erfunden und dürfen nicht als reale Herstellerinformation verwendet werden.

## Zweck und Geltungsbereich {#scope}

CertaAccess ist eine fiktive Identity-Security-Plattform für Workforce Identity, privilegierte Zugriffe und nicht-menschliche Identitäten. Dieses Dokument beschreibt die Demo-Version 3.8 für den erfundenen Kunden Nordstern Bank.

## Bereitstellung und Mandantentrennung {#deployment}

Die Plattform wird als logisch mandantengetrennter SaaS-Dienst bereitgestellt. Anwendungsdaten jedes Demo-Tenants werden über eine eindeutige Tenant-ID adressiert. Produktive Mandanten teilen sich keine Datenbankschemata; kryptografische Schlüssel werden pro Tenant verwaltet.

Für diese Portfolio-Demo existiert keine echte Mandantenfähigkeit. Die Aussagen dieses Dokuments sind ausschließlich Teil des synthetischen Verkaufsszenarios.

## Region und Verfügbarkeit {#region-availability}

Für europäische Kunden kann die primäre Datenhaltung in der Region Frankfurt gewählt werden. Backups verbleiben innerhalb des Europäischen Wirtschaftsraums.

Die fiktive Servicezusage beträgt 99,95 % monatliche Verfügbarkeit. Ausgenommen sind angekündigte Wartungsfenster und vom Kunden verursachte Ausfälle. Diese Zahl ist ein synthetischer Demonstrationswert und kein echtes SLA.

## Verschlüsselung {#encryption}

Persistente Kundeninhalte werden im Ruhezustand mit AES-256 verschlüsselt. Die Übertragung zu Browsern und APIs verwendet TLS 1.3; TLS 1.2 bleibt für kompatible Integrationen als Mindestversion zulässig. Unsichere Protokolle und Cipher Suites werden nicht angeboten.

## Standards und Schnittstellen {#standards}

CertaAccess unterstützt SAML 2.0 und OpenID Connect für Single Sign-on sowie SCIM 2.0 für Provisionierung und Deprovisionierung. Eine REST-API ist für genehmigte administrative Integrationen vorgesehen.

## Rollen und Administration {#roles}

Das vordefinierte Rollenmodell trennt Tenant Administration, Security Review, Application Ownership und reine Leserechte. Kritische Konfigurationsänderungen werden im Audit Log erfasst.

## Historische MFA-Beschreibung {#legacy-mfa}

In Version 3.8 konnten administrative Konten entweder TOTP oder WebAuthn als zweiten Faktor verwenden. Eine Tenant-Richtlinie konnte WebAuthn erzwingen, war aber nicht standardmäßig aktiv.

> **Aktualitätshinweis:** Diese Beschreibung gilt nur für Version 3.8. Neuere Anforderungen sind der aktuellen Authentifizierungsrichtlinie zu entnehmen.

## Historische Audit-Aufbewahrung {#legacy-retention}

Version 3.8 speicherte Audit-Ereignisse standardmäßig 365 Tage. Längere Aufbewahrung erforderte zu diesem Zeitpunkt einen externen SIEM-Export.

> **Aktualitätshinweis:** Aktuelle Aufbewahrungswerte sind im jeweils gültigen Security-&-Operations-Handbuch festgelegt.

## Dokumentverantwortung {#ownership}

Fiktiver Owner: CertaAccess Product Management. Nächste synthetische Überprüfung: 15. November 2026.
