import { Activity, Binary, Braces, ChevronRight, Fingerprint, ShieldCheck } from 'lucide-react';
import type { Analysis } from '../types';

export function TechnicalProof({ analysis }: { analysis: Analysis }) {
  const proof = analysis.technicalProof ?? {
    retrievalVersion: 'hybrid-pgvector-fts-rrf-v1',
    promptVersion: 'claims-v1',
    embeddingModel: 'bge-m3',
    chatModel: 'llama3.1:8b',
    citationValidation: 'Claim- und Zitatprüfung aktiv',
    dataBoundary: 'Lokale Verarbeitung auf 127.0.0.1',
  };
  const citations = analysis.questions.flatMap((question) => question.citations);
  const provenanceCount = citations.filter((citation) => citation.retrieval).length;

  return (
    <details className="panel technical-proof-panel">
      <summary>
        <span className="technical-proof-title">
          <span className="insight-icon"><Activity size={18} /></span>
          <span><span className="eyebrow">Techniknachweis · v0.2</span><strong>Wie dieses Ergebnis abgesichert wurde</strong></span>
        </span>
        <span className="technical-proof-summary">
          <span><ShieldCheck size={14} /> Fail-closed</span>
          <span><Binary size={14} /> Hybrid RAG</span>
          <span><Fingerprint size={14} /> Auditierbar</span>
        </span>
        <ChevronRight className="technical-proof-chevron" size={17} />
      </summary>
      <div className="technical-proof-grid">
        <div><Activity size={17} /><span><small>Pipelinephase</small><strong>{analysis.phase ?? analysis.status}</strong><em>Persistenter Lauf · Versuch {analysis.attempt ?? 1} von höchstens 2</em></span></div>
        <div><Braces size={17} /><span><small>Retrieval</small><strong>Dense + FTS → RRF</strong><em>{proof.retrievalVersion}</em></span></div>
        <div><Fingerprint size={17} /><span><small>Zitationen</small><strong>{citations.length} serverseitig geprüft</strong><em>{provenanceCount > 0 ? `${provenanceCount} mit Retrieval-Provenienz` : proof.citationValidation}</em></span></div>
        <div><Activity size={17} /><span><small>Modelle</small><strong>{proof.chatModel}</strong><em>{proof.embeddingModel} · {proof.promptVersion}</em></span></div>
        <div><ShieldCheck size={17} /><span><small>Datenbereich</small><strong>{proof.dataBoundary}</strong><em>Keine Retrieval-Ränge werden als KI-Konfidenz ausgegeben.</em></span></div>
      </div>
    </details>
  );
}
