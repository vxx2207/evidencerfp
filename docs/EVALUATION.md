# Reproduzierbare Qualitätsmessung

EvidenceRFP bewertet Retrieval und beleggebundene Antworten gegen ein vollständig synthetisches,
deutsches Golden Set. Version 2 enthält 20 Kernfragen mit je drei kuratierten Paraphrasen, also
80 Evaluationsfälle. Dataset-, Chunking-, Retrieval-, Prompt- und Modellversion stehen direkt in
[`examples/golden-set.json`](../examples/golden-set.json).

```powershell
cd backend
.\mvnw.cmd -Dtest=GoldenSetEvaluationTest test
```

Der Lauf erzeugt zwei lokale, reproduzierbare Artefakte:

- `backend/target/evaluation/golden-set-report.json` für CI und Vergleiche;
- `backend/target/evaluation/golden-set-report.html` für die Portfolio-Demo.

Die Gates messen `Recall@6`, Mean Reciprocal Rank, Status-Macro-F1,
Required-Term-Abdeckung, Zitationspräzision sowie Precision und Recall für `NOT_FOUND`. Die
deterministische Baseline läuft in jedem CI-Lauf. Ein echter `bge-m3`-/Ollama-Lauf bleibt separat,
damit normale Builds reproduzierbar und ohne Modelldownload funktionieren.

## Referenzlauf 0.2.0

| Metrik | Ergebnis | Gate |
|---|---:|---:|
| Fälle | 80 | mindestens 80 |
| Recall@6 | 100 % | mindestens 95 % |
| MRR | 97,8 % | mindestens 85 % |
| Status-Macro-F1 | 100 % | mindestens 90 % |
| Required-Term-Abdeckung | 100 % | mindestens 95 % |
| Zitationspräzision | 100 % | exakt 100 % |
| `NOT_FOUND` Precision / Recall | 100 % / 100 % | exakt 100 % / 100 % |

Der schnelle Bericht verwendet den deterministischen lexikalischen Retriever
`lexical-deterministic-v2`. Zusätzlich führt die Testcontainers-Suite dasselbe 80-Fälle-Set durch
den Production-Retriever `hybrid-pgvector-fts-rrf-v1`. Dieser Test erzwingt Recall@6 und MRR und
weist nach, dass sowohl der Dense- als auch der deutsche Full-Text-Pfad tatsächlich Ergebnisse
beisteuern. Dadurch bleiben normale Auswertungen schnell, während CI mit Docker die reale
PostgreSQL-/pgvector-Implementierung prüft.

Jeder Einzelfall enthält erwarteten und ermittelten Status, ersten relevanten Rang, fehlende
Pflichtbegriffe, zitierte Quellen, Zitattexte und die vollständige Retrieval-Reihenfolge. Alle
Messwerte beziehen sich ausschließlich auf synthetische Daten und sind keine Kundenergebnisse.

Retrieval-Scores sind ausschließlich Rangsignale. Sie werden nicht als KI-Konfidenz interpretiert.

## Optionaler echter Ollama-/bge-m3-Lauf

Das Maven-Profil `ollama-eval` ist ein separater, ausdrücklich aktivierter Integrationslauf. Es
verwendet nicht die deterministischen Test-Doubles, sondern startet PostgreSQL/pgvector über
Testcontainers, lädt den echten Production-Retriever `hybrid-pgvector-fts-rrf-v1`, erzeugt Dense-
Embeddings mit `bge-m3` und lässt `llama3.1:8b` alle 80 synthetischen Fälle im Claim-JSON-Schema
beantworten. Der Bericht enthält pro Fall beide Retrieval-Ränge, RRF-Score, Chunk-Hash,
Zitationen, Antwortstatus und Providerlaufzeit.

Voraussetzungen:

- Java 21 und ein erreichbarer Docker-Daemon für `pgvector/pgvector:pg17`;
- ein laufendes Ollama auf `http://127.0.0.1:11434` (oder `OLLAMA_BASE_URL`);
- lokal geladene Modelle `llama3.1:8b` und `bge-m3`;
- ausreichend Arbeitsspeicher und Zeit für 80 echte Chat-Aufrufe.

PowerShell:

```powershell
ollama pull llama3.1:8b
ollama pull bge-m3
$env:EVIDENCERFP_RUN_OLLAMA_EVAL = "true"
$env:OLLAMA_BASE_URL = "http://127.0.0.1:11434"
cd backend
.\mvnw.cmd -Pollama-eval verify
Remove-Item Env:EVIDENCERFP_RUN_OLLAMA_EVAL
```

Bash:

```bash
ollama pull llama3.1:8b
ollama pull bge-m3
cd backend
EVIDENCERFP_RUN_OLLAMA_EVAL=true \
  OLLAMA_BASE_URL=http://127.0.0.1:11434 \
  ./mvnw -Pollama-eval verify
```

Die versionierten Artefakte liegen anschließend unter
`backend/target/evaluation/ollama/`. Ihr Dateiname enthält Dataset-, Retrieval-, Chatmodell- und
Embeddingmodell-Version; JSON und HTML enthalten zusätzlich Prompt-/Chunking-Version, die
gemessenen Qualitätsgates und die konkreten Adapterklassen. Unterschreitet der echte Lauf ein
Gate, werden die Berichte zuerst geschrieben und Maven endet danach mit einem Fehler.

Ohne `EVIDENCERFP_RUN_OLLAMA_EVAL=true` wird der Test auch bei aktiviertem Profil nur kompiliert
und als übersprungen ausgewiesen. CI aktiviert genau diesen Nicht-Opt-in-Modus und verlangt exakt
einen kontrollierten Skip; der echte Providerlauf bleibt ausschließlich manuell beziehungsweise
Nightly. Normale Builds laden deshalb keine Modelle und benötigen für diesen Lauf weder Ollama noch Docker.
