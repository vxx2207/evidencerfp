import {
  AlertOctagon,
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  BadgeCheck,
  Ban,
  BookOpen,
  Check,
  CheckCircle2,
  ChevronRight,
  CircleDashed,
  Clock3,
  Edit3,
  FileCheck2,
  FileText,
  Fingerprint,
  Highlighter,
  Link2,
  LockKeyhole,
  MessageSquareText,
  Save,
  Search,
  ShieldCheck,
  Sparkles,
  X,
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import type { Analysis, Citation, EvidenceDocument, Question, QuestionUpdate } from '../types';
import { isReviewDecided } from '../review';
import { AnswerBadge, ReviewBadge } from './StatusBadge';

function CitationDrawer({ citation, document, onClose }: { citation: Citation; document?: EvidenceDocument; onClose: () => void }) {
  const section = document?.sections.find((item) => item.id === citation.chunkId);
  const content = section?.content ?? citation.quote;
  const quoteIndex = content.indexOf(citation.quote);
  const before = quoteIndex >= 0 ? content.slice(0, quoteIndex) : '';
  const after = quoteIndex >= 0 ? content.slice(quoteIndex + citation.quote.length) : '';

  return (
    <div className="drawer-shell" role="dialog" aria-modal="true" aria-labelledby="citation-title">
      <button className="drawer-backdrop" onClick={onClose} aria-label="Quellenansicht schließen" />
      <aside className="citation-drawer">
        <div className="drawer-header">
          <div><span className="eyebrow">Verifizierte Quelle</span><h2 id="citation-title">{document?.title ?? 'Evidenzdokument'}</h2></div>
          <button className="icon-button" onClick={onClose} aria-label="Schließen"><X size={19} /></button>
        </div>
        <div className="document-meta-strip"><span><FileText size={14} /> {document?.type ?? 'PDF'}</span><span>{document?.version}</span><span>Seite {citation.page}</span></div>
        <div className="document-page">
          <div className="document-page-header"><strong>CertaAccess</strong><span>CONFIDENTIAL · SYNTHETIC</span></div>
          <span className="section-number">{citation.section.split(' ')[0]}</span>
          <h3>{citation.section.replace(/^\d+(\.\d+)?\s*/, '')}</h3>
          <p>{quoteIndex >= 0 ? <>{before}<mark>{citation.quote}</mark>{after}</> : <mark>{citation.quote}</mark>}</p>
          <div className="document-filler"><i /><i /><i /><i /></div>
          <span className="page-number">{citation.page}</span>
        </div>
        <div className="citation-proof">
          <div className="proof-icon"><Fingerprint size={18} /></div>
          <div><strong>Zitat serverseitig validiert</strong><span>Chunk-ID <code>{citation.chunkId}</code> ist Teil des Retrieval-Sets. Die markierte Passage stimmt exakt überein.</span></div>
          <BadgeCheck size={20} />
        </div>
      </aside>
    </div>
  );
}

export function Workbench({
  analysis,
  activeQuestion,
  onSelectQuestion,
  onUpdateQuestion,
  tourCitationId,
}: {
  analysis: Analysis;
  activeQuestion: Question;
  onSelectQuestion: (question: Question) => void;
  onUpdateQuestion: (questionId: string, update: QuestionUpdate) => Promise<void>;
  tourCitationId?: string;
}) {
  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState(false);
  const [answer, setAnswer] = useState(activeQuestion.answer);
  const [selectedCitation, setSelectedCitation] = useState<Citation | null>(null);
  const [saving, setSaving] = useState(false);
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle');

  useEffect(() => {
    setAnswer(activeQuestion.answer);
    setEditing(false);
    setSelectedCitation(null);
    setCopyState('idle');
  }, [activeQuestion.id, activeQuestion.answer]);

  useEffect(() => {
    if (!tourCitationId) return;
    const citation = activeQuestion.citations.find((item) => item.id === tourCitationId);
    if (citation) setSelectedCitation(citation);
  }, [activeQuestion.citations, tourCitationId]);

  const visibleQuestions = useMemo(() => analysis.questions.filter((question) => `${question.id} ${question.text}`.toLowerCase().includes(query.toLowerCase())), [analysis.questions, query]);
  const activeIndex = analysis.questions.findIndex((question) => question.id === activeQuestion.id);
  const sourceDocument = (citation: Citation) => analysis.documents.find((document) => document.id === citation.documentId);

  async function update(update: QuestionUpdate) {
    setSaving(true);
    try {
      await onUpdateQuestion(activeQuestion.id, update);
      setEditing(false);
    } finally {
      setSaving(false);
    }
  }

  function move(offset: number) {
    const next = analysis.questions[activeIndex + offset];
    if (next) onSelectQuestion(next);
  }

  async function copyClarification() {
    if (!activeQuestion.clarification) return;
    try {
      await navigator.clipboard.writeText(activeQuestion.clarification);
      setCopyState('copied');
    } catch {
      setCopyState('failed');
    }
  }

  const approvalBlocked = activeQuestion.status === 'NOT_FOUND' || Boolean(activeQuestion.conflict);

  return (
    <div className="workbench-page">
      <div className="workbench-toolbar">
        <div>
          <span className="eyebrow">Answer Workbench</span>
          <span className="workbench-progress"><i style={{ width: `${((activeIndex + 1) / analysis.questions.length) * 100}%` }} /></span>
          <strong>{activeIndex + 1} / {analysis.questions.length}</strong>
        </div>
        <div className="workbench-nav">
          <button className="button button-ghost compact" disabled={activeIndex === 0} onClick={() => move(-1)}><ArrowLeft size={15} /> Zurück</button>
          <button className="button button-ghost compact" disabled={activeIndex === analysis.questions.length - 1} onClick={() => move(1)}>Weiter <ArrowRight size={15} /></button>
        </div>
      </div>

      <div className="workbench-grid">
        <aside className="question-rail">
          <div className="rail-header"><strong>Fragen</strong><span>{analysis.questions.filter((q) => isReviewDecided(q.reviewStatus)).length} entschieden</span></div>
          <label className="rail-search"><Search size={15} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Suchen …" /></label>
          <div className="rail-list">
            {visibleQuestions.map((question) => (
              <button key={question.id} className={question.id === activeQuestion.id ? 'active' : ''} onClick={() => onSelectQuestion(question)}>
                <span className={`rail-status status-${question.status.toLowerCase()}`}>{question.reviewStatus === 'FREIGEGEBEN' ? <Check size={12} /> : question.reviewStatus === 'BLOCKIERT' ? <Ban size={11} /> : null}</span>
                <span><small>{question.id} · {question.category}</small><strong>{question.text}</strong></span>
                <ChevronRight size={14} />
              </button>
            ))}
          </div>
        </aside>

        <main className="answer-canvas">
          <div className="answer-header">
            <div className="answer-meta"><AnswerBadge status={activeQuestion.status} /><ReviewBadge status={activeQuestion.reviewStatus} /><span><Clock3 size={13} /> {activeQuestion.updatedAt}</span></div>
            <span className="question-number">{activeQuestion.id}</span>
            <h1>{activeQuestion.text}</h1>
            <span className="category-chip">{activeQuestion.category}</span>
          </div>

          {activeQuestion.conflict && (
            <div className="conflict-banner">
              <div className="conflict-title"><AlertOctagon size={20} /><div><strong>{activeQuestion.conflict.title}</strong><span>Automatische Freigabe deaktiviert</span></div></div>
              <div className="conflict-values"><span><small>Quelle A</small>{activeQuestion.conflict.sourceA}</span><span><small>Quelle B</small>{activeQuestion.conflict.sourceB}</span></div>
            </div>
          )}

          <section className="answer-section">
            <div className="section-heading"><div><Sparkles size={17} /><h2>Antwortentwurf</h2><span>KI-assistiert</span></div>{!editing && <button className="text-button" onClick={() => setEditing(true)}><Edit3 size={14} /> Bearbeiten</button>}</div>
            {editing ? (
              <div className="answer-editor"><textarea value={answer} onChange={(event) => setAnswer(event.target.value)} autoFocus /><div><span>{answer.length} Zeichen</span><button className="button button-ghost compact" onClick={() => { setAnswer(activeQuestion.answer); setEditing(false); }}>Abbrechen</button><button className="button button-primary compact" disabled={saving || !answer.trim()} onClick={() => update({ answer })}><Save size={14} /> Speichern</button></div></div>
            ) : <p className="answer-copy">{activeQuestion.answer}</p>}
          </section>

          <section className="rationale-box">
            <div><MessageSquareText size={17} /><strong>Warum diese Antwort?</strong></div>
            <p>{activeQuestion.rationale}</p>
          </section>

          {activeQuestion.citations.length > 0 ? (
            <section className="inline-sources">
              <div className="section-heading"><div><Link2 size={17} /><h2>Belegstellen</h2><span>{activeQuestion.citations.length}</span></div></div>
              <div className="inline-source-list">
                {activeQuestion.citations.map((citation, index) => (
                  <button key={citation.id} onClick={() => setSelectedCitation(citation)}>
                    <span className="source-index">{index + 1}</span>
                    <span><strong>{sourceDocument(citation)?.title}</strong><small>{citation.section} · Seite {citation.page}</small></span>
                    <Highlighter size={16} />
                  </button>
                ))}
              </div>
            </section>
          ) : (
            <section className="no-evidence-state">
              <CircleDashed size={26} /><div><strong>Keine belastbare Evidenz gefunden</strong><span>Die Antwort wurde bewusst nicht ergänzt. Nutze die interne Klärungsfrage für dein Follow-up.</span></div>
            </section>
          )}

          {activeQuestion.clarification && (
            <section className="clarification-card"><div className="clarification-label"><span>Interne Klärungsfrage</span><LockKeyhole size={14} /></div><p>{activeQuestion.clarification}</p><button className="text-button" onClick={() => void copyClarification()}>{copyState === 'copied' ? 'Kopiert' : copyState === 'failed' ? 'Kopieren fehlgeschlagen' : 'In Zwischenablage kopieren'}</button></section>
          )}

          <div className="review-actions">
            <div><span>Deine Entscheidung</span><small>Änderungen werden im Review-Protokoll erfasst.</small></div>
            <button className="button button-danger" disabled={saving || activeQuestion.reviewStatus === 'BLOCKIERT'} onClick={() => update({ reviewStatus: 'BLOCKIERT' })}><X size={16} /> Blockieren</button>
            <button className="button button-approve" disabled={saving || approvalBlocked || activeQuestion.reviewStatus === 'FREIGEGEBEN'} title={approvalBlocked ? 'Konflikt oder fehlende Evidenz muss zuerst geklärt werden.' : undefined} onClick={() => update({ reviewStatus: 'FREIGEGEBEN' })}><Check size={16} /> Freigeben</button>
          </div>
        </main>

        <aside className="evidence-panel">
          <div className="evidence-panel-title"><div><ShieldCheck size={18} /><strong>Evidenzprüfung</strong></div><span className="verified-chip"><Check size={11} /> serverseitig</span></div>
          <div className="validation-list">
            <div className="validation-item"><span className="validation-ok"><Check size={12} /></span><div><strong>Chunk autorisiert</strong><small>Nur abgerufene Quellen zugelassen</small></div></div>
            <div className="validation-item"><span className="validation-ok"><Check size={12} /></span><div><strong>Zitat stimmt überein</strong><small>Exakte Textpassage gefunden</small></div></div>
            <div className="validation-item"><span className={activeQuestion.conflict ? 'validation-warning' : 'validation-ok'}>{activeQuestion.conflict ? <AlertTriangle size={12} /> : <Check size={12} />}</span><div><strong>{activeQuestion.conflict ? 'Quellenkonflikt' : 'Quelle aktuell'}</strong><small>{activeQuestion.conflict ? 'Manuelle Entscheidung nötig' : 'Keine veraltete Evidenz'}</small></div></div>
          </div>
          <div className="evidence-divider"><span>RETRIEVAL SET</span><i /></div>
          <div className="retrieval-list">
            {activeQuestion.citations.map((citation, index) => {
              const document = sourceDocument(citation);
              return (
                <button key={citation.id} onClick={() => setSelectedCitation(citation)}>
                  <div className="retrieval-top"><span className="source-index">{index + 1}</span><span className="file-kind"><FileText size={12} /> {document?.type}</span><span>Seite {citation.page}</span></div>
                  <strong>{document?.title}</strong>
                  <p>„{citation.quote}“</p>
                  <div><span><FileCheck2 size={13} /> {citation.relevance}</span><ArrowRight size={14} /></div>
                </button>
              );
            })}
            {activeQuestion.citations.length === 0 && <div className="retrieval-empty"><BookOpen size={22} /><strong>Retrieval ohne Treffer</strong><span>Kein Chunk erfüllt die Evidenzschwelle.</span></div>}
          </div>
          <div className="trust-footer"><Fingerprint size={18} /><div><strong>Evidence Lock</strong><span>Manipulationsschutz aktiv</span></div><CheckCircle2 size={18} /></div>
        </aside>
      </div>

      {selectedCitation && <CitationDrawer citation={selectedCitation} document={sourceDocument(selectedCitation)} onClose={() => setSelectedCitation(null)} />}
    </div>
  );
}
