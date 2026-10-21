# Beispieldaten erzeugen

Die versionierten Dateien unter `examples/source/`, `examples/rfp-questions.json` und `examples/identity-snapshot.json` sind die lesbare Source of Truth. Der Backend-`SampleDataGenerator` erzeugt aus `examples/source/` lokale Binär-Fixtures:

- fünf textbasierte Dokumente im PDF-/DOCX-Format,
- `rfp-security-questionnaire.xlsx` mit 24 Fragen.

Der IAM-Snapshot bleibt als lesbare JSON-Datei unter `examples/identity-snapshot.json` erhalten und kann direkt hochgeladen werden; für ihn wird keine Binärkopie erzeugt.

Generierte Eingabedateien landen ausschließlich unter `examples/generated/`. Das Verzeichnis ist in `.gitignore` eingetragen und darf nicht committed werden. Davon getrennt liegt unter `examples/output/` genau ein bewusst versioniertes, vollständig synthetisches Referenz-Evidence-Pack. Es dient Recruitern und Reviewern als direkt prüfbares Ergebnis und wird nicht als Eingabequelle verwendet.

## Aufruf

Vom Repository-Root:

```bash
cd backend
./mvnw exec:java -Dexec.args="../examples/source ../examples/generated"
```

Unter PowerShell:

```powershell
Set-Location backend
./mvnw.cmd exec:java `
  "-Dexec.args=../examples/source ../examples/generated"
```

Der Maven Exec-Einstiegspunkt ist `com.certaaccess.evidencerfp.sample.SampleDataGenerator`. Ein Lauf ersetzt vorhandene generierte Dateien deterministisch, ohne Quelldateien zu verändern.

## Vier-Minuten-Deep-Dive aufnehmen

Nach `npm ci` im Verzeichnis `frontend/` lässt sich die vollständige synthetische Demo vom Repository-Root reproduzierbar aufnehmen:

```powershell
node scripts/record-deep-dive.mjs
```

Das Script startet bei Bedarf die lokale Vite-Demo auf `127.0.0.1:4173`, zeichnet die fünf festen Tour-Stationen bei 1440 × 900 Pixeln auf, blendet deutsche Abschnittstitel und Untertitel direkt ins Bild ein, exportiert das Evidence Pack und setzt den Demozustand am Ende zurück. Das Ergebnis landet unter `docs/assets/evidencerfp-deep-dive-4m.webm`; die separate WebVTT-Datei liegt daneben.

Ein schneller Funktionscheck ohne versioniertes Video dauert nur wenige Sekunden:

```powershell
node scripts/record-deep-dive.mjs --dry-run
```

Mit `EVIDENCERFP_DEMO_URL` kann stattdessen eine bereits laufende Demo angegeben werden. Auch dann bleiben sämtliche sichtbaren Daten synthetisch.

## Erwartete Validierung

Nach der Erzeugung:

1. prüfen, dass exakt fünf textbasierte Evidenzdateien und ein XLSX vorliegen,
2. RFP-Parser gegen alle 24 Fragen ausführen,
3. Dokument-Parser gegen jede generierte Datei ausführen,
4. Golden-Set-Evaluation starten und Recall@6 dokumentieren,
5. sicherstellen, dass keine Datei echte Metadaten oder lokale Pfade enthält.

## Warum nur ein binäres Referenzergebnis?

Lesbare Quellen lassen sich reviewen, vergleichen und sicher als synthetisch kennzeichnen. Wiederholt erzeugte Eingabefixtures würden die Git-Historie aufblähen und ihre Prüfung erschweren. Das kleine Referenz-Evidence-Pack ist die bewusst begrenzte Ausnahme: Es wurde über den realen Browserexport erzeugt, technisch gerendert und schreibgeschützt in Microsoft Excel geprüft.
