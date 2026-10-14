---
documentId: CA-PAM-2026
title: CertaAccess Privileged Access und Identity Lifecycle
version: "4.1"
publishedAt: 2026-06-10
validUntil: 2027-06-09
classification: SYNTHETISCHE-DEMOQUELLE
---

# CertaAccess Privileged Access und Identity Lifecycle

> **SYNTHETISCHE DEMOQUELLE:** Alle Kontrollen und Prozesswerte sind frei erfunden und ausschließlich für die EvidenceRFP-Portfolio-Demo bestimmt.

## Just-in-Time-Zugriff {#jit-access}

Privilegierte Rollen können zeitlich begrenzt aktiviert werden. Eine genehmigte Aktivierung gilt standardmäßig 60 Minuten und endet danach automatisch. Dauer, Zielsystem, Begründung und Genehmiger werden als Audit-Ereignis gespeichert.

## Vier-Augen-Prinzip {#four-eyes}

Rollen mit der Klassifizierung `critical` erfordern vor jeder Aktivierung zwei Genehmigungen: eine durch den fachlichen Application Owner und eine durch Security Operations. Antragsteller dürfen ihren eigenen Antrag nicht genehmigen.

## Joiner, Mover, Leaver {#joiner-mover-leaver}

Über SCIM 2.0 eingehende Deaktivierungen werden innerhalb von 15 Minuten verarbeitet. Nach einer bestätigten Deaktivierung beendet CertaAccess aktive Sitzungen und entfernt direkt zugewiesene privilegierte Rollen. Gruppenbasierte Zuweisungen werden bei der nächsten Verzeichnissynchronisation neu bewertet.

## Verwaiste privilegierte Konten {#orphaned-accounts}

Ein privilegiertes Konto gilt als verwaist, wenn kein aktiver menschlicher Owner referenziert ist oder der referenzierte Owner den Beschäftigungsstatus `DEPARTED` besitzt. Der tägliche Lifecycle-Scan erstellt ein kritisches Finding und empfiehlt die sofortige Deaktivierung. Die Demo führt keine automatische Deaktivierung aus.

## Regelmäßige Rezertifizierung {#access-review}

Privilegierte Rollen werden quartalsweise durch Application Owner rezertifiziert. Nicht rechtzeitig bestätigte Zuweisungen werden nach einer siebentägigen Nachfrist suspendiert und als Audit-Ereignis exportiert.

## Break-glass {#break-glass}

Break-glass-Konten sind von regulären JIT-Abläufen ausgenommen, müssen aber einem benannten Security Owner gehören. Jede Nutzung erzeugt eine kritische Benachrichtigung und eine nachgelagerte Pflichtprüfung innerhalb eines Arbeitstags.

## Dokumentverantwortung {#ownership}

Fiktiver Owner: CertaAccess Privileged Access Team. Letzte synthetische Überprüfung: 10. Juni 2026.
