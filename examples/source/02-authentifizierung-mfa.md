---
documentId: CA-AUTH-2026
title: CertaAccess Authentifizierungs- und MFA-Richtlinie
version: "4.2"
publishedAt: 2026-08-15
validUntil: 2027-08-14
classification: SYNTHETISCHE-DEMOQUELLE
---

# CertaAccess Authentifizierungs- und MFA-Richtlinie

> **SYNTHETISCHE DEMOQUELLE:** CertaAccess, seine Richtlinien und alle genannten Kontrollen sind erfunden. Dieses Dokument dient nur als überprüfbare Eingabe für EvidenceRFP.

## Geltung {#scope}

Diese Richtlinie gilt ab CertaAccess Version 4.2 und ersetzt ältere Aussagen zur Authentifizierung administrativer Konten.

## Phishing-resistente Faktoren {#phishing-resistant-mfa}

Als phishing-resistent gelten in CertaAccess FIDO2-Sicherheitsschlüssel und WebAuthn-Plattform-Authentikatoren mit Benutzerverifikation. SMS, E-Mail-Codes, TOTP und Push ohne kryptografische Bindung gelten nicht als phishing-resistent.

## Privilegierte Konten {#privileged-mfa}

Alle aktivierten privilegierten menschlichen Konten müssen mindestens einen phishing-resistenten Faktor registrieren. Ab Version 4.2 erzwingt die Plattform diese Anforderung standardmäßig und verhindert eine privilegierte Sitzung, wenn ausschließlich TOTP, SMS oder Passwort verfügbar ist.

Diese Aussage widerspricht der historischen Beschreibung für Version 3.8 nicht technisch, ändert aber die aktuelle Antwort auf die Frage, welche Faktoren für Administratoren zulässig sind. EvidenceRFP soll beide Fundstellen anzeigen und eine bewusste Quellenentscheidung verlangen.

## Workforce-Konten {#workforce-mfa}

Für nicht privilegierte Workforce-Konten kann der Tenant WebAuthn, FIDO2-Sicherheitsschlüssel oder TOTP zulassen. Risikobasierte Richtlinien dürfen stärkere Faktoren für sensible Anwendungen verlangen.

## Wiederherstellung {#recovery}

Die Wiederherstellung eines privilegierten Kontos erfordert zwei voneinander unabhängige Genehmiger aus dem Security-Operations-Team. Ein temporärer Recovery-Zugang ist auf 30 Minuten begrenzt, wird vollständig protokolliert und zwingt anschließend zur Registrierung eines neuen phishing-resistenten Faktors.

## Lokale Passwörter {#passwords}

Wo lokale Passwörter erforderlich sind, werden sie mit Argon2id und einem pro Credential eindeutigen Salt gespeichert. Klartextpasswörter werden weder protokolliert noch über Support-Schnittstellen ausgegeben.

## Sitzungen {#sessions}

Privilegierte Browser-Sitzungen laufen nach 15 Minuten Inaktivität ab und haben eine absolute Höchstdauer von acht Stunden. Eine erneute Authentifizierung ist vor besonders kritischen Richtlinienänderungen erforderlich.

## Ausnahmen {#exceptions}

Ausnahmen von der MFA-Pflicht sind für privilegierte menschliche Konten nicht vorgesehen. Break-glass-Konten verwenden offline verwahrte FIDO2-Schlüssel und werden bei jeder Nutzung als kritisches Ereignis gemeldet.

## Dokumentverantwortung {#ownership}

Fiktiver Owner: CertaAccess Identity Security. Letzte synthetische Überprüfung: 15. August 2026.
