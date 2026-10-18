# ADR 0002: Hybrid Retrieval mit Reciprocal Rank Fusion

- Status: Angenommen
- Datum: 2026-10-17
- Entscheidung für: EvidenceRFP 0.2

## Kontext

Reines Dense Retrieval findet semantische Paraphrasen gut, kann aber exakte Produktbegriffe, Normnummern oder Zahlen übersehen. Reine Volltextsuche ist bei synonymen Formulierungen schwächer. Ein einzelner, nicht kalibrierter Score darf außerdem nicht wie eine Antwortkonfidenz wirken.

## Entscheidung

Für jede Frage werden zwei unabhängige Kandidatenlisten erzeugt:

1. pgvector Dense Retrieval mit Top 12;
2. PostgreSQL Full-Text-Suche mit deutscher Sprachkonfiguration und Top 12.

Die Listen werden mit Reciprocal Rank Fusion und `k = 60` zusammengeführt. Die besten sechs Chunks bilden den erlaubten Evidenzkontext. Version 0.2 verwendet keinen zusätzlichen Reranker.

Gespeichert werden Chunk-Hash, Retrievalkanäle, Einzelränge, Fusionsrang sowie Retrieval- und Embeddingversion. Diese Werte dienen ausschließlich der Provenienz. Die Anwendung zeigt daraus keinen KI-Konfidenzwert an.

Antworten werden als Claims mit Zitationsreferenzen erzeugt. Nur Zitate aus dem abgerufenen Top-6-Kontext sind erlaubt; Quote, Zahlen und Negationen werden serverseitig geprüft.

## Konsequenzen

Retrieval-Entscheidungen sind reproduzierbar und auditierbar. Die Qualität wird mit Recall@1/3/6, MRR und nDCG@6 gemessen. Änderungen an Chunking, Embeddings oder Fusion benötigen eine neue Versionskennung und einen vollständigen Eval-Lauf.
