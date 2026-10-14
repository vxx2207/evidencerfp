# Architektur

## Zielbild

EvidenceRFP ist ein modularer Monolith. Fachlogik, Sicherheitsgrenzen und Datenhaltung bleiben im Spring-Boot-Backend; das React-Frontend kennt ausschließlich das gemeinsame Gateway-Datenmodell. Das erleichtert eine eindrucksvolle öffentliche Demo, ohne Upload- oder KI-Funktionen ins Internet zu stellen.

```mermaid
flowchart TB
    subgraph Browser
        UI[Review UI]
        GW{AnalysisGateway}
        UI --> GW
        GW --> DEMO[DemoAnalysisGateway]
        GW --> LIVE[ApiAnalysisGateway]
    end

    DEMO --> JSON[statische synthetische Fixtures]
    LIVE --> REST[REST API]

    subgraph Backend
        REST --> APP[Analysis Application Service]
        APP --> ING[Import & Datei-Validierung]
        APP --> SEARCH[Retrieval]
        APP --> CTRL[IAM Controls]
        APP --> REVIEW[Review & Freigabe]
        APP --> EXPORT[XLSX Export]
        SEARCH --> CITE[Zitations-Validator]
        SEARCH --> AI[Chat-/Embedding-Provider]
    end

    ING --> PG[(PostgreSQL + pgvector)]
    SEARCH --> PG
    REVIEW --> PG
    AI --> OLLAMA[Ollama lokal]
```

## Module und Verantwortlichkeiten

| Modul | Verantwortlichkeit | Nicht verantwortlich für |
|---|---|---|
| Import | Dateiformat, MIME/Magic Bytes, Größenlimit, Parserauswahl | Fachliche Bewertung |
| Evidence | Normalisierung, Chunking, Metadaten, Retrieval | Freigabe von Antworten |
| Analysis | Orchestrierung, strukturierte Antwortentwürfe | Autoritative Security-Entscheidungen |
| Citation | Chunk-Zugehörigkeit und wörtliche Fundstelle prüfen | Neue Belege erzeugen |
| IAM Controls | Drei deterministische Regeln auf Snapshot-Daten | Änderungen am IdP |
| Review | Editieren, Konfliktsperre, Human-in-the-loop-Freigabe | Automatische Genehmigung bei Konflikten |
| Export | Formula-Injection-sicheres XLSX Evidence Pack | Originaldateien verändern |

## Datenfluss

1. Ein Upload wird vor dem Parsen anhand Erweiterung, deklariertem MIME-Type, Magic Bytes und Größenlimit geprüft.
2. Text wird normalisiert, mit Dokument- und Abschnittsmetadaten in Chunks zerlegt und eingebettet. Im Offline-Production-Profil entstehen deterministische Hash-Vektoren; mit `ollama` liefert `bge-m3` semantische 1.024-dimensionale Embeddings.
3. Je Frage werden höchstens sechs relevante Chunks abgerufen und als nicht vertrauenswürdiger Kontext an das Modell übergeben.
4. Das Modell liefert ein strukturiertes Ergebnis. Der Server akzeptiert nur bekannte Statuswerte und Zitationen auf abgerufene Chunks.
5. Eine Zitation ist nur gültig, wenn der normalisierte Zitatausschnitt im referenzierten Chunk enthalten ist.
6. Der IAM-Snapshot wird getrennt davon durch deterministische Regeln verarbeitet.
7. Konflikte, veraltete Quellen und fehlende Belege werden als Findings gespeichert und beeinflussen den Freigabestatus.
8. Nur der vom Menschen geprüfte Stand wird exportiert.

## Frontend-Modi

`DemoAnalysisGateway` lädt unveränderliche, vorberechnete Ergebnisse. Es besitzt keine Upload-Funktion und sendet keine Daten an einen Server. `ApiAnalysisGateway` spricht mit der lokalen REST-API. Beide implementieren dasselbe Interface, sodass UI und Demo-Story identisch bleiben.

Die Auswahl erfolgt beim Build über `VITE_APP_MODE=demo|api`. Für GitHub Pages wird ausschließlich `demo` verwendet.

## Entscheidungsprotokoll

- **Modularer Monolith statt Microservices:** weniger Betriebsaufwand, aber klare Fachgrenzen.
- **PostgreSQL plus pgvector:** Transaktions- und Vektordaten bleiben konsistent in einem System.
- **Profilabhängige Embeddings:** Offline-Hashing hält den Stack ohne Modell lauffähig; `production,ollama` ersetzt es über denselben Port durch `bge-m3`.
- **Deterministische IAM-Regeln:** Findings sind reproduzierbar und nicht vom Sprachmodell abhängig.
- **Lokales Ollama als Default:** keine Dokumentübertragung an einen Cloud-Provider.
- **Synchroner MVP-Import:** Der Analyse-Lauf endet innerhalb des POST-Requests. Eine separate Status-Ressource hält den API-Vertrag für einen späteren Job-Runner stabil.
- **Keine numerische KI-Konfidenz:** Status und überprüfbare Evidenz sind erklärbarer als scheinpräzise Scores.

Im `production`-Profil werden Analysen, Fragen, Review-Status, Findings und Dokumentmetadaten relational in PostgreSQL gespeichert. Retrieval-Chunks liegen nur für die Dauer einer Suchsession im pgvector-Arbeitsindex und werden danach entfernt. Das Demo-Profil verwendet absichtlich ein In-Memory-Repository.

## Laufzeitannahmen

- Eine Instanz, eine lokale Datenbank, keine Mandantentrennung.
- Analyse-Daten gelten als temporär und können über den DELETE-Endpunkt entfernt werden.
- Alle Beispiele sind synthetisch. Für reale Daten fehlen im MVP unter anderem Authentifizierung, Autorisierung, Mandantenisolation und eine belastbare Aufbewahrungsrichtlinie.
