# Beispieldaten erzeugen

Die versionierten Dateien unter `examples/source/`, `examples/rfp-questions.json` und `examples/identity-snapshot.json` sind die lesbare Source of Truth. Der Backend-`SampleDataGenerator` erzeugt aus `examples/source/` lokale Binär-Fixtures:

- fünf textbasierte Dokumente im PDF-/DOCX-Format,
- `rfp-security-questionnaire.xlsx` mit 24 Fragen.

Der IAM-Snapshot bleibt als lesbare JSON-Datei unter `examples/identity-snapshot.json` erhalten und kann direkt hochgeladen werden; für ihn wird keine Binärkopie erzeugt.

Generierte Dateien landen ausschließlich unter `examples/generated/`. Das Verzeichnis ist in `.gitignore` eingetragen und darf nicht committed werden.

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

## Erwartete Validierung

Nach der Erzeugung:

1. prüfen, dass exakt fünf textbasierte Evidenzdateien und ein XLSX vorliegen,
2. RFP-Parser gegen alle 24 Fragen ausführen,
3. Dokument-Parser gegen jede generierte Datei ausführen,
4. Golden-Set-Evaluation starten und Recall@6 dokumentieren,
5. sicherstellen, dass keine Datei echte Metadaten oder lokale Pfade enthält.

## Warum keine Binärdateien im Repository?

Lesbare Quellen lassen sich reviewen, vergleichen und sicher als synthetisch kennzeichnen. Generierte Binärdateien blähen die Git-Historie auf und erschweren die Prüfung ihrer Herkunft. Reproduzierbare Erzeugung hält beides getrennt.
