# ADR 0001: Persistente Analysejobs mit Lease und idempotenter Wiederholung

- Status: Angenommen
- Datum: 2026-10-17
- Entscheidung für: EvidenceRFP 0.2

## Kontext

Die erste Version führte Analysen in einem lokalen Executor aus und hielt die Uploads im Arbeitsspeicher. Nach einem Prozessabbruch konnte eine Analyse dauerhaft in `PROCESSING` verbleiben. Außerdem konnte ein Worker eine zwischenzeitlich gelöschte Analyse durch ein abschließendes UPSERT erneut anlegen.

## Entscheidung

Jeder Import wird vor der Annahme validiert, in einem lokalen Artefaktspeicher abgelegt und zusammen mit einem persistenten Job registriert. Worker claimen fällige Jobs atomar über PostgreSQL `FOR UPDATE SKIP LOCKED`.

- Lease: 30 Sekunden
- Heartbeat: 10 Sekunden
- maximale Versuche: 2
- Recovery: abgelaufene Jobs beginnen idempotent von vorn
- Finalisierung: Compare-and-Set nur durch den aktuellen Lease-Besitzer
- Abbruch: Tombstone beziehungsweise `cancel_requested`; niemals abschließendes UPSERT

Partielle Retrieval- oder Antwortdaten eines fehlgeschlagenen Versuchs werden über die Versuch-ID isoliert. Rohdateien werden nach einem terminalen Zustand entfernt.

Review-Änderungen verwenden eine Revision pro Frage. Das atomische Fragenupdate und das append-only Audit-Ereignis liegen in derselben Transaktion.

## Konsequenzen

Ein Backend-Neustart verliert keine angenommenen Jobs. Mehrere Worker können dieselbe Queue sicher verwenden. Verarbeitungsschritte müssen idempotent sein; die Anwendung benötigt bis zum Jobabschluss lokalen Speicherplatz für die validierten Uploadartefakte.
