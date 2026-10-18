# EvidenceRFP

![Release](https://img.shields.io/badge/release-v0.2.0-0f766e)
![CI](https://img.shields.io/badge/CI-GitHub_Actions_ready-2088ff)
![GitHub Pages](https://img.shields.io/badge/demo-GitHub_Pages_ready-222222)
![License](https://img.shields.io/badge/license-MIT-2f855a)

![EvidenceRFP – Belegbare Security-Antworten](docs/assets/social-preview.png)

> **Portfolio-Demo · vollständig synthetische Daten**
>
> EvidenceRFP ist kein Produkt der fiktiven Unternehmen CertaAccess oder Nordstern Bank. Die öffentliche Demo verarbeitet keine Uploads und keine echten Kundendaten.

**Vom Security-Fragebogen zur belegbaren Antwort – inklusive IAM-Kontrollnachweisen.**

EvidenceRFP unterstützt Technical-Sales-Teams dabei, wiederkehrende Security-Fragebögen schneller und belastbarer zu beantworten. Die Anwendung findet passende Aussagen in freigegebenen Produktunterlagen, zeigt die exakte Fundstelle, markiert Widersprüche und ergänzt dokumentbasierte Antworten um deterministische Identity-Security-Prüfungen.

> **Schnelleinstieg:** Die Anwendung besitzt eine geführte **60-Sekunden-Demo**. Lokal starten, auf „60-Sekunden-Demo starten“ klicken und nacheinander verifizierte Quelle, Quellenkonflikt, bewusstes Nichtwissen, IAM-Finding und Evidence Pack erleben. Der GitHub-Pages-Workflow ist vorbereitet; der öffentliche Link wird nach dem ersten Push des noch nicht verbundenen Repositories ergänzt.

| 80 Golden-Set-Fälle | 100 % Recall@6 | 100 % technisch validierte Zitationen | 3 deterministische IAM-Regeln |
|---:|---:|---:|---:|
| deutsch, synthetisch, versioniert | MRR 97,8 % in der CI-Baseline | Claim, Chunk und Zitat serverseitig geprüft | ohne LLM-Entscheidung |

[![Geführte EvidenceRFP-Tour: Quelle, Konflikt, NOT_FOUND, IAM-Finding und Human Gate](docs/assets/evidencerfp-tour.gif)](docs/assets/evidencerfp-90s.webm)

*Acht Sekunden durch die fünf festen Tour-Stationen; Klick öffnet die [90-Sekunden-Aufnahme](docs/assets/evidencerfp-90s.webm) mit separaten [WebVTT-Untertiteln](docs/assets/evidencerfp-90s.vtt). Vollständig synthetisch.*

![EvidenceRFP Answer Workbench mit geöffneter, exakt markierter Quellenpassage](docs/assets/evidence-review.png)

*Echter Screenshot der laufenden React-Anwendung; alle sichtbaren Namen, Dokumente und Ergebnisse sind synthetisch.*

| Quellenkonflikt stoppt die Freigabe | Deterministische IAM-Kontrolle |
|---|---|
| ![IAM-020 zeigt widersprüchliche Aufbewahrungsfristen und eine gesperrte Freigabe](docs/assets/evidence-conflict.png) | ![IAM Findings zeigt das reproduzierbare MFA-Finding finding-001](docs/assets/evidence-finding.png) |
| `IAM-020`: 365 und 180 Tage werden nebeneinander gezeigt. | `finding-001`: Regel, betroffenes Objekt, Nachweis und Maßnahme sind nachvollziehbar. |

*Aktuelle Aufnahmen der geführten Demo bei 1440 × 900 Pixeln; vollständig synthetisch.*

Das zugehörige [synthetische Beispiel-Evidence-Pack](examples/output/EvidenceRFP-Synthetic-Evidence-Pack.xlsx) lässt sich direkt in Excel öffnen und enthält die vier Blätter `Antworten`, `IAM Findings`, `Quellen` und `Review`.

## Der 60-Sekunden-Scan

| Recruiter-Frage | Antwort |
|---|---|
| Welches Problem wird gelöst? | RFP-Antworten sind langsam, schwer prüfbar und anfällig für unbelegte Aussagen. |
| Für wen? | Sales Engineers, Presales Consultants, Security- und IAM-Teams. |
| Was ist technisch interessant? | Dokument-Retrieval, serverseitig validierte Zitationen, kontrollierte Human-in-the-loop-Freigabe und regelbasierte IAM-Checks. |
| Wo liegt der Geschäftswert? | Weniger Rechercheaufwand, kürzere Antwortzeiten und ein nachvollziehbarer Audit-Trail. |
| Was macht die Demo glaubwürdig? | Jede Antwort ist `SUPPORTED`, `PARTIAL` oder `NOT_FOUND`; fehlende Evidenz wird nie erfunden. |
| Was kann ich sofort ansehen? | Die statische GitHub-Pages-Demo nutzt vorberechnete, klar gekennzeichnete Ergebnisse – ohne Backend und ohne Upload. |

## Warum dieses Projekt?

Identity Security verbindet technische Tiefe mit einem klaren Presales-Prozess: Anforderungen verstehen, Risiken sichtbar machen, einen PoC abgrenzen und Nutzen nachvollziehbar erklären. Die Projektauswahl orientiert sich an öffentlich zugänglichen Einblicken von [ENISA](https://www.enisa.europa.eu/sites/default/files/2026-02/NIS%20Investments%202025%20-%20Main%20report.pdf) und [ISC2](https://www.isc2.org/Insights/2025/12/2025-ISC2-Cybersecurity-Workforce-Study) sowie an realen Rollenprofilen bei [EY](https://careers.ey.com/ey/job/Berlin-Berater-IAM-Cyber-Security-%28wmd%29-BE-10117/1193467201/) und [Palo Alto Networks](https://jobs.paloaltonetworks.com/en/job/munich/domain-consultant-solution-engineer/47263/101871697968). Die verlinkten Organisationen stehen in keiner Verbindung zu EvidenceRFP.

## Vier Minuten Demo-Story

1. **Import:** Ein Security-Fragebogen der fiktiven Nordstern Bank wird geladen.
2. **Analyse:** Fünf synthetische CertaAccess-Dokumente werden durchsucht; ein IAM-Snapshot durchläuft drei reproduzierbare Kontrollen.
3. **Review:** Eine belegte Antwort öffnet die genaue Quellpassage. Ein Widerspruch wird nebeneinander angezeigt. Eine unbeantwortbare Frage erzeugt eine interne Klärungsfrage.
4. **Entscheidung:** Der Sales Engineer korrigiert und genehmigt Antworten. Konflikte bleiben bis zur Klärung gesperrt.
5. **Übergabe:** Der geprüfte Stand wird in beiden Modi als Excel Evidence Pack mit Antworten, Findings, Quellen und Reviewstatus exportiert.

Der vollständige Moderationstext steht im [Demo Talk Track](docs/DEMO_TALK_TRACK.md).

## Kernfunktionen

- Import eines RFP im XLSX-Format (`ID`, `Question`, optional `Category`)
- Verarbeitung textbasierter PDF- und DOCX-Produktunterlagen
- Antworten mit prüfbaren Dokument-, Abschnitts- und Chunk-Zitationen
- Antwortstatus `SUPPORTED`, `PARTIAL` und `NOT_FOUND` statt erfundener Prozentwerte
- Erkennung fehlender, widersprüchlicher oder veralteter Evidenz
- Deterministische Kontrollen für privilegierte Konten, verwaiste Konten und alte Service-Account-Zugangsdaten
- Bearbeitungs- und Freigabe-Workflow mit sicherem XLSX-Export
- Zwei Frontend-Modi mit demselben Datenmodell: statische Demo-Fixtures oder lokale API
- geführte, per Hash-Link teilbare 60-Sekunden-Tour und reproduzierbarer Demo-Reset
- echtes XLSX Evidence Pack auch in der statischen Browser-Demo
- Geführter lokaler Importdialog für XLSX, mehrere PDF/DOCX-Dateien und einen optionalen IAM-Snapshot
- neustartfeste PostgreSQL-Jobs mit Lease, Heartbeat, maximal zwei Versuchen und atomarer Finalisierung
- optimistische Review-Sperre über `If-Match`, Fragenrevision und transaktionales Audit-Ereignis
- Hybrid Retrieval aus pgvector und deutscher PostgreSQL-Volltextsuche mit Reciprocal Rank Fusion
- persistierte Retrieval-Provenienz und einzelne, serverseitig verifizierte Claims
- gehärtete OOXML-/PDF-Verarbeitung mit Parser-, Dekompressions-, Seiten-, Zeichen- und Chunkbudgets

## Architektur

```mermaid
flowchart LR
    U[Sales Engineer] --> UI[React + TypeScript]
    UI -->|DemoAnalysisGateway| FIX[Vorberechnete Fixtures]
    UI -->|ApiAnalysisGateway| API[Spring Boot API]
    API --> IMP[Import & gehärtete Validierung]
    API --> JOB[(Persistente Jobs + Leases)]
    JOB --> ORCH[Worker + Recovery]
    IMP --> DOC[PDF / DOCX / XLSX Parser]
    DOC --> RET[450/60 Token Chunking]
    RET --> HYB[Dense + deutsche FTS + RRF]
    HYB --> DB[(PostgreSQL + pgvector)]
    ORCH --> LLM[Ollama / optionaler Provider]
    ORCH --> IAM[Deterministische IAM-Regeln]
    LLM --> CIT[Claim-, Zitations- und Negationsprüfung]
    IAM --> REV[Review-Ergebnis]
    CIT --> REV
    REV --> EXP[XLSX Evidence Pack]
```

**Vertrauensgrenze:** Uploads und Dokumenttexte sind nicht vertrauenswürdig. Nur das Backend validiert Dateityp, Retrieval-Kontext, Zitationen, Statusübergänge und Exportdaten. Das Modell besitzt keine Tools und darf keine Aktionen ausführen. Mehr dazu im [Threat Model](docs/THREAT_MODEL.md).

## Technologie

- Java 21, Spring Boot 4.1.x, Spring AI 2.0.x
- React, TypeScript, Vite
- PostgreSQL, Flyway, pgvector
- Apache POI sowie PDF-/Tika-Reader
- deterministischer Offline-Modus ohne KI-Schlüssel
- Ollama mit `llama3.1:8b` und `bge-m3` als lokale KI-Voreinstellung
- optionaler, bewusst zu aktivierender OpenAI-Adapter mit eigenem API-Key
- OpenAPI-3.1-Vertrag und daraus reproduzierbar generierter TypeScript-Client
- Micrometer-/Prometheus-Metriken und strukturierte, inhaltsfreie Korrelationslogs
- JUnit, Testcontainers, Vitest, Testing Library und Playwright

## Quickstart: öffentliche Demo lokal ansehen

Voraussetzungen: Node.js 22+ und npm.

```bash
cd frontend
npm ci
npm run dev
```

Die Frontend-Voreinstellung nutzt synthetische Demo-Fixtures. Falls nötig:

```bash
VITE_APP_MODE=demo npm run dev
```

Unter PowerShell lautet die letzte Zeile:

```powershell
$env:VITE_APP_MODE = "demo"
npm run dev
```

Danach entweder auf **60-Sekunden-Demo starten** klicken oder direkt eine Station öffnen, zum Beispiel `http://localhost:5173/#tour/source`, `#tour/conflict`, `#tour/not-found`, `#tour/finding` oder `#tour/export`.

## Quickstart: vollständiger lokaler Stack

Voraussetzungen: Java 21, Node.js 22+, Docker Desktop und optional eine lokale Ollama-Installation.

1. Konfiguration anlegen und Datenbank starten:

   ```bash
   cp .env.example .env
   docker compose up -d postgres
   ```

2. Lokale Modelle bereitstellen – entweder mit installiertem Ollama:

   ```bash
   ollama pull llama3.1:8b
   ollama pull bge-m3
   ```

   oder per Docker-Profil:

   ```bash
   docker compose --profile ai up -d ollama
   docker compose exec ollama ollama pull llama3.1:8b
   docker compose exec ollama ollama pull bge-m3
   ```

3. Backend starten:

   ```bash
   cd backend
   ./mvnw -Pproduction,ollama spring-boot:run -Dspring-boot.run.profiles=production,ollama
   ```

   Unter Windows: `mvnw.cmd -Pproduction,ollama spring-boot:run "-Dspring-boot.run.profiles=production,ollama"`. Falls kein Maven Wrapper vorhanden ist, lässt sich derselbe Aufruf mit `mvn` ausführen. Die eingecheckten Entwicklungswerte sind ausschließlich für localhost gedacht; geänderte Werte aus `.env` müssen auch als Prozessvariablen des Backends gesetzt werden.

   Ohne Sprachmodell kann das Backend reproduzierbar mit `-Pproduction` und dem Profil `production` gestartet werden. Dann verwendet es den regelbasierten Antwortgenerator und deterministische 1.024-dimensionale Hash-Embeddings. Mit `production,ollama` stammen Chat-Antworten von `llama3.1:8b` und Embeddings tatsächlich von `bge-m3`.

4. Frontend in einem zweiten Terminal mit API-Modus starten:

   ```bash
   cd frontend
   npm ci
   VITE_APP_MODE=api VITE_API_BASE_URL=http://localhost:8080 npm run dev
   ```

5. In der Oberfläche **Neue Analyse** wählen und die erzeugten Beispieldaten hochladen. Die Binär-Fixtures lassen sich reproduzierbar aus den lesbaren Quellen erzeugen; der Ablauf steht in [scripts/README.md](scripts/README.md).

Die Anwendung ist anschließend unter `http://localhost:5173` erreichbar; die API unter `http://localhost:8080/api/v1`.

### Optionaler Cloud-Provider

Nur wenn eine externe Verarbeitung ausdrücklich gewollt ist, kann statt Ollama der mitgelieferte OpenAI-Adapter aktiviert werden:

```bash
export OPENAI_API_KEY="..."
cd backend
./mvnw -Pproduction,openai spring-boot:run -Dspring-boot.run.profiles=production,openai
```

In diesem Modus verlassen Dokumentpassagen die lokale Umgebung. Für reale Kundendaten ist der MVP ausdrücklich nicht freigegeben; siehe [Security und Grenzen](docs/SECURITY_LIMITATIONS.md).

## API auf einen Blick

| Methode | Pfad | Zweck |
|---|---|---|
| `GET` | `/api/v1/capabilities` | Laufzeitfähigkeiten und aktivierter KI-Provider |
| `POST` | `/api/v1/analyses` | RFP, Evidenzdateien und optionalen IAM-Snapshot einreichen; optional mit `Idempotency-Key` |
| `GET` | `/api/v1/analyses/{id}` | Fortschritt und Verarbeitungsstatus abrufen |
| `GET` | `/api/v1/analyses/{id}/result` | Fragen, Antworten, vollständige Quellenpassagen, Findings und Reviewereignisse laden |
| `PATCH` | `/api/v1/analyses/{id}/questions/{questionId}` | Antwort bearbeiten oder freigeben; verlangt `If-Match` mit Fragenrevision |
| `GET` | `/api/v1/analyses/{id}/export.xlsx` | Geprüftes Evidence Pack exportieren |
| `DELETE` | `/api/v1/analyses/{id}` | Analyse und zugehörige lokale Daten löschen |

Der Import ist als persistenter lokaler Hintergrundjob umgesetzt: `POST /analyses` validiert und speichert die Dateien unter servergenerierten Namen, liefert sofort eine Analyse-ID mit `PROCESSING`, und das Frontend fragt anschließend Phase und echten Fortschritt bis `COMPLETED` oder `FAILED` ab. Worker claimen Jobs atomar, erneuern eine 30-Sekunden-Lease alle zehn Sekunden und übernehmen abgebrochene Läufe höchstens einmal neu. Ergebnis, Review und Export sind vor `COMPLETED` mit `409 ANALYSIS_NOT_READY` gesperrt. Der vollständige Vertrag liegt als [OpenAPI 3.1](docs/openapi/evidence-rfp-v1.yaml) vor.

## Portfolio-Unterlagen

- [Architekturentscheidungen](docs/ARCHITECTURE.md)
- [Reproduzierbare Evaluation](docs/EVALUATION.md)
- [Threat Model](docs/THREAT_MODEL.md)
- [Discovery Brief](docs/DISCOVERY_BRIEF.md)
- [ROI Case](docs/ROI_CASE.md)
- [PoC Success Plan](docs/POC_SUCCESS_PLAN.md)
- [Einwandbehandlung](docs/OBJECTION_HANDLING.md)
- [Security und Grenzen](docs/SECURITY_LIMITATIONS.md)
- [90-Sekunden-Storyboard und Aufnahmeplan](docs/DEMO_90_SECONDS.md)
- [Demo Talk Track](docs/DEMO_TALK_TRACK.md)
- [Release-Checkliste](docs/RELEASE_CHECKLIST.md)
- [Beispieldaten und erwartete Ergebnisse](examples/README.md)

## Qualitätsziele

- Deterministische CI-Baseline über **80 deutsche Fälle**: Recall@6 **100 %**, MRR **97,8 %**, Status-Macro-F1 **100 %** und Required-Term-Abdeckung **100 %**.
- Zitationspräzision sowie `NOT_FOUND` Precision und Recall liegen im versionierten synthetischen Set bei **100 %**.
- 100 % ausgegebener Zitationen zeigen auf einen abgerufenen Chunk und ein darin enthaltenes Zitat; Zahlen, zentrale Entitäten und Negationen werden zusätzlich geprüft.
- Testcontainers deckt Migrationen, Hybrid Retrieval, zwei Worker, Recovery, Review-Parallelität und XLSX-Export ab; Vitest und Playwright sichern Oberfläche, Tour, Deep Links und Download.

## Bewusste Grenzen

Kein Login, keine Mandantenfähigkeit, kein OCR, keine echte IdP- oder CRM-Verbindung, keine automatische Kontosperrung und keine rechtliche Compliance-Bewertung. Die Anwendung ist ein Portfolio-MVP und verarbeitet ausschließlich synthetische Daten. Details und sichere Betriebsannahmen stehen in [Security und Grenzen](docs/SECURITY_LIMITATIONS.md).

## Mitwirken

Beiträge sind willkommen. Bitte zuerst [CONTRIBUTING.md](CONTRIBUTING.md) und [SECURITY.md](SECURITY.md) lesen.

## Lizenz

[MIT](LICENSE) · © 2026 EvidenceRFP Contributors
