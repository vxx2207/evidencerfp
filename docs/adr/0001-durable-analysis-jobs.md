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
- Abbruch: `cancel_requested` plus logisch verborgenes `deleted_at`-Tombstone in derselben
  Transaktion; physisches Löschen erst nach erfolgreichem Upload-Cleanup; niemals abschließendes UPSERT

Partielle Retrieval- oder Antwortdaten eines fehlgeschlagenen Versuchs werden nicht finalisiert.
Jeder terminale Übergang schreibt atomar einen Eintrag in `analysis_cleanup_task`. Cleanup-Worker
verwenden ebenfalls Lease und `FOR UPDATE SKIP LOCKED`; erst nach erfolgreicher, idempotenter
Dateilöschung werden die Uploadmetadaten entfernt und der erledigte Auftrag atomar gelöscht. Nach einem
Absturz wird ein abgelaufener Cleanup-Lease übernommen. Ein einstündiger Orphan-Sweeper deckt den
kleinen Zeitraum zwischen Dateispeicherung und transaktionaler Jobregistrierung ab.
Ein transaktionaler Seed-Marker verhindert außerdem, dass eine bewusst gelöschte synthetische
Demo-ID bei einem späteren Backendstart erneut angelegt wird.

Review-Änderungen verwenden eine Revision pro Frage. Das atomische Fragenupdate und das append-only Audit-Ereignis liegen in derselben Transaktion.

## Konsequenzen

Ein Backend-Neustart verliert weder angenommene Jobs noch bereits registrierte Dateibereinigungen.
Mehrere Worker können beide Queues sicher verwenden. Verarbeitung und Cleanup müssen idempotent
sein; bei wiederholten Dateisystemfehlern können Rohdateien bis zu einem späteren Recovery-Lauf
liegen bleiben und benötigen entsprechend lokalen Speicherplatz.
