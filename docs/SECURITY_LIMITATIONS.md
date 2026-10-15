# Security und Grenzen

## Sicherheitsprinzipien des MVP

- **Evidenz vor Eloquenz:** Eine gute Formulierung ersetzt keinen gültigen Beleg.
- **Server entscheidet:** Status, Zitationen, Freigaben und Export werden nicht dem Browser oder Modell überlassen.
- **Keine LLM-Aktionen:** Das Modell erhält keine Tools, Systemzugriffe oder frei nutzbaren Netzwerkfunktionen.
- **Lokale Voreinstellung:** Dokumente und Embeddings bleiben im Demo-/Ollama-Betrieb bei Anwendung, Ollama und PostgreSQL auf dem Rechner.
- **Human in the loop:** Kein Entwurf wird ohne explizite Prüfung zur autoritativen Kundenaussage.
- **Minimierte Demo:** GitHub Pages enthält nur vorberechnete synthetische JSON-Daten und keinen Upload.

## Upload-Grenzen

Unterstützt werden XLSX für Fragen sowie textbasierte PDF- und DOCX-Dateien für Evidenz. Zulässige Endung, MIME-Type und Magic Bytes müssen übereinstimmen. Dateianzahl, Einzelgröße und Gesamtgröße sind begrenzt. Dateinamen werden nie als Speicherpfad verwendet. Ein eigenes Dekompressions- und Parserzeitbudget ist im MVP noch nicht implementiert.

Nach der Validierung werden Uploads für den lokalen Hintergrundjob im Arbeitsspeicher gepuffert. Worker- und Queue-Anzahl sind begrenzt, dennoch ist dieser Mechanismus nur für die lokale Einzelnutzer-Demo ausgelegt. Ein produktiver Dienst benötigt externes Job- und Objektspeicher-Management, Quoten und belastbare Backpressure.

Die konkrete Konfiguration des Backends ist maßgeblich; öffentlich dokumentierte Limits dürfen nicht großzügiger sein als die tatsächlich erzwungenen Werte.

## Datenschutz und Datenhaltung

Der Portfolio-MVP ist ausschließlich für synthetische Daten vorgesehen. Auch bei lokaler Ausführung können Inhalte in PostgreSQL, Logs, temporären Parserdateien, Backups und Embeddings verbleiben. Der DELETE-Endpunkt löscht die Anwendungsdaten einer Analyse, ist aber kein zertifizierter Löschprozess über externe Backups oder Snapshots.

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

`SUPPORTED` bedeutet: Der Entwurf besitzt eine technisch validierte Fundstelle im bereitgestellten Korpus. Es bedeutet nicht, dass die Quelle wahr, aktuell, vollständig oder rechtlich ausreichend ist. `PARTIAL` kennzeichnet unvollständige oder konflikthafte Evidenz. `NOT_FOUND` bedeutet, dass im abgerufenen Korpus kein ausreichend belegbarer Entwurf vorlag.

IAM-Findings sind reproduzierbare Prüfungen des bereitgestellten Snapshots, keine Echtzeit-Aussage über einen Tenant und kein Penetrationstest.

## Vor einer produktiven Nutzung

Erforderlich sind mindestens belastbare AuthN/AuthZ, Mandantenisolation, Secret Management, Verschlüsselung und Schlüsselrotation, Malware-Prüfung, Datenklassifizierung, Aufbewahrungs- und Löschregeln, manipulationssicheres Audit Logging, Monitoring/Alerting, Dependency- und Container-Scanning, Backup/Restore-Tests, Rate Limits sowie ein unabhängiger Security- und Datenschutzreview.

Sicherheitslücken bitte gemäß [SECURITY.md](../SECURITY.md) melden.
