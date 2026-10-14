# Einwandbehandlung

## „Das ist doch nur ein Chatbot über PDFs.“

Ein Chatfenster ist nicht der Kern. EvidenceRFP bildet einen kontrollierten RFP-Prozess ab: strukturierter Import, Retrieval, serverseitig geprüfte Zitationen, Konflikterkennung, deterministische IAM-Regeln, Human Review und sicherer XLSX-Export. Ein Modell darf weder Quellen erfinden noch Aktionen ausführen.

## „Wir können den Antworten einer KI nicht vertrauen.“

Das sollen Sie auch nicht müssen. Jede Aussage bleibt ein Entwurf. Der Status beschreibt die Evidenzlage statt eine vermeintliche KI-Sicherheit. Ohne gültigen Chunk und darin enthaltene Textstelle ist eine Antwort nicht belegt; Konflikte verhindern die Freigabe.

## „Unsere Dokumente dürfen keine Cloud verlassen.“

Der Standardbetrieb nutzt Ollama lokal. Ein Cloud-Provider ist optional und muss ausdrücklich konfiguriert werden. Für einen realen Einsatz wären zusätzlich DLP, Secret Management, Aufbewahrungsregeln und eine vertragliche Bewertung nötig.

## „Unsere Fragebögen haben jedes Mal andere Spalten.“

Das MVP unterstützt bewusst eine kleine, dokumentierte Struktur mit `ID`, `Question` und optional `Category`. Ein produktiver Import-Assistent könnte Spalten zuordnen; im PoC wird zunächst eine Vorlage genutzt, damit Qualitätsmessung und Fehlerbilder eindeutig bleiben.

## „Unsere PDFs sind gescannt.“

OCR liegt außerhalb des MVP. Nur textbasierte PDF- und DOCX-Dateien werden unterstützt. Das ist eine bewusste Sicherheits- und Scope-Entscheidung; OCR wäre ein eigener Verarbeitungsschritt mit Qualitätsmetriken.

## „Warum nicht einfach eine Antwortbibliothek?“

Eine Antwortbibliothek ist schnell, verliert aber leicht Aktualität und Kontext. EvidenceRFP verbindet freigegebene Dokumentpassagen, Versionshinweise und Tenant-spezifische Kontrollen. Eine bestehende Bibliothek kann später eine zusätzliche Quelle sein.

## „Kann das System Antworten automatisch an Kunden senden?“

Nein. Im MVP wird weder E-Mail noch CRM angebunden. Der Mensch prüft und exportiert. Diese Grenze schützt vor falschen oder rechtlich bindenden Zusagen.

## „Wie beweisen Sie den ROI?“

Nicht mit erfundenen Benchmarks. Vor dem PoC werden aktive Bearbeitungszeit, Volumen und Reviewaufwand gemessen. Das [ROI-Modell](ROI_CASE.md) legt jede Annahme und Formel offen und zählt Qualitäts- oder Deal-Effekte nicht doppelt.

## „Was passiert bei widersprüchlichen Dokumenten?“

Beide Passagen werden sichtbar gemacht. Der Fall erhält ein Finding und kann nicht automatisch freigegeben werden. Der zuständige Reviewer entscheidet, welche Quelle gilt; die Dokumentverantwortlichen können den Ursprungskonflikt beheben.

## „Ist das bereits produktionsreif?“

Nein. Es ist ein nachvollziehbarer Portfolio-MVP. Authentifizierung, Rollen, Mandantenisolation, produktive Observability, Malware-Prüfung und mehrere weitere Kontrollen sind vor echten Kundendaten erforderlich und transparent dokumentiert.
