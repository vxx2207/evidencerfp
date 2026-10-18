# Security und Grenzen

## Sicherheitsprinzipien des MVP

- **Evidenz vor Eloquenz:** Eine gute Formulierung ersetzt keinen gültigen Beleg.
- **Server entscheidet:** Status, Zitationen, Freigaben und Export werden nicht dem Browser oder Modell überlassen.
- **Keine LLM-Aktionen:** Das Modell erhält keine Tools, Systemzugriffe oder frei nutzbaren Netzwerkfunktionen.
- **Lokale Voreinstellung:** Dokumente und Embeddings bleiben im Demo-/Ollama-Betrieb bei Anwendung, Ollama und PostgreSQL auf dem Rechner. Production-Backend sowie veröffentlichte PostgreSQL-/Ollama-Ports binden standardmäßig ausschließlich an `127.0.0.1`.
- **Human in the loop:** Kein Entwurf wird ohne explizite Prüfung zur autoritativen Kundenaussage.
- **Minimierte Demo:** GitHub Pages enthält nur vorberechnete synthetische JSON-Daten und keinen Upload.

## Upload-Grenzen

Unterstützt werden XLSX für Fragen sowie textbasierte PDF- und DOCX-Dateien für Evidenz. Zulässige Endung, MIME-Type und Magic Bytes müssen übereinstimmen. Dateianzahl, Einzelgröße und Gesamtgröße sind begrenzt. Dateinamen werden nie als Speicherpfad verwendet. PDF ist auf 200 Seiten begrenzt; pro Datei gelten eine Million extrahierte Zeichen und 2.000 Chunks. OOXML wird auf 100 MB dekomprimierten Inhalt, ein Verhältnis von 100:1 und 2.000 Einträge begrenzt. Makros, OLE-Pakete, externe Beziehungen, Verschlüsselung, doppelte ZIP-Einträge und Pfad-Traversal werden abgelehnt. Jeder Parserlauf besitzt ein 20-Sekunden-Budget.

Nach der Validierung werden Uploads bis zum terminalen Jobzustand in einem geschützten lokalen Verzeichnis unter servergenerierten Namen gehalten. Persistente Jobs besitzen Lease, Heartbeat, Recovery und maximal zwei Versuche; Erfolg, Fehler und Abbruch räumen die Rohdateien auf. Worker- und Queue-Anzahl sind begrenzt, dennoch ist dieser Mechanismus nur für die lokale Einzelnutzer-Demo ausgelegt. Ein produktiver Dienst benötigt verschlüsselten Objekt-/Artefaktspeicher, Quoten und belastbare Backpressure.

Die konkrete Konfiguration des Backends ist maßgeblich; öffentlich dokumentierte Limits dürfen nicht großzügiger sein als die tatsächlich erzwungenen Werte.

## Datenschutz und Datenhaltung

Der Portfolio-MVP ist ausschließlich für synthetische Daten vorgesehen. Auch bei lokaler Ausführung können Inhalte in PostgreSQL, vollständigen Quellenpassagen, Reviewereignissen, Logs, temporären Parserdateien, Backups und Embeddings verbleiben. Der DELETE-Endpunkt löscht die Anwendungsdaten einer Analyse, ist aber kein zertifizierter Löschprozess über externe Backups oder Snapshots.

Cloud-KI wird nur über das explizite Maven- und Spring-Profil `openai` genutzt. Dabei werden abgerufene Dokumentpassagen an den konfigurierten Provider übertragen. Wer das Profil aktiviert, ist für Providervertrag, Region, Aufbewahrung, Trainingseinstellungen und Schlüsselverwaltung verantwortlich.

## Nicht im MVP

- Authentifizierung, Autorisierung und Rollenmodell
- Mandantentrennung und kundenspezifische Schlüssel
- OCR und Verarbeitung handschriftlicher Inhalte
- Malware-Sandbox und Content Disarm & Reconstruction
- echte IdP-, SIEM-, CRM- oder Ticketing-Anbindung
- automatische Änderungen an Konten oder Richtlinien
- qualifizierte elektronische Signatur oder rechtliche Freigabe
- Hochverfügbarkeit, Disaster Recovery, SLO und 24/7-Betrieb
- vollständige DSGVO-, ISO-27001-, SOC-2- oder branchenspezifische Compliance

## Interpretationsgrenzen

`SUPPORTED` bedeutet: Alle ausgegebenen Claims besitzen technisch validierte Fundstellen im bereitgestellten Korpus. Dabei werden erlaubte Chunk-ID, exaktes Zitat, Zahlen, zentrale Entitäten und Negationen geprüft. Es bedeutet nicht, dass die Quelle wahr, aktuell, vollständig oder rechtlich ausreichend ist. `PARTIAL` kennzeichnet nur teilweise belegte oder konflikthafte Evidenz. `NOT_FOUND` bedeutet, dass im abgerufenen Korpus kein belegbarer Claim vorlag. Providerausfall und ungültige Modellausgabe sind technische Analysefehler und ausdrücklich kein `NOT_FOUND`.

IAM-Findings sind reproduzierbare Prüfungen des bereitgestellten Snapshots, keine Echtzeit-Aussage über einen Tenant und kein Penetrationstest.

## Vor einer produktiven Nutzung

Erforderlich sind mindestens belastbare AuthN/AuthZ, Mandantenisolation, Secret Management, Verschlüsselung und Schlüsselrotation, Malware-Prüfung, Datenklassifizierung, Aufbewahrungs- und Löschregeln, manipulationssicheres Audit Logging, Monitoring/Alerting, Dependency- und Container-Scanning, Backup/Restore-Tests, Rate Limits sowie ein unabhängiger Security- und Datenschutzreview.

Sicherheitslücken bitte gemäß [SECURITY.md](../SECURITY.md) melden.
