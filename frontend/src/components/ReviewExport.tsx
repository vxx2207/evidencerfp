import {
  AlertTriangle,
  ArrowRight,
  Check,
  CheckCircle2,
  Download,
  FileCheck2,
  FileSpreadsheet,
  LockKeyhole,
  ShieldCheck,
  XCircle,
} from 'lucide-react';
import type { Analysis, AppView, Capabilities, Question } from '../types';
import { evidencePackFilename } from '../format';
import { PageIntro } from './Chrome';
import { AnswerBadge } from './StatusBadge';

export function ReviewExport({
  analysis,
  capabilities,
  onOpenQuestion,
  onNavigate,
  onExport,
  exporting,
}: {
  analysis: Analysis;
  capabilities: Capabilities;
  onOpenQuestion: (question: Question) => void;
  onNavigate: (view: AppView) => void;
  onExport: () => void;
  exporting: boolean;
}) {
  const approved = analysis.questions.filter((q) => q.reviewStatus === 'FREIGEGEBEN');
  const blocked = analysis.questions.filter((q) => q.reviewStatus === 'BLOCKIERT');
  const open = analysis.questions.filter((q) => q.reviewStatus === 'OFFEN');
  const ready = open.length === 0 && blocked.length === 0;
  const progress = Math.round((approved.length / analysis.questions.length) * 100);

  return (
    <div className="page review-page">
      <PageIntro eyebrow="Final Gate" title="Review & Export" description="Freigabestatus prüfen, offene Punkte auflösen und das Evidence Pack für den Kunden vorbereiten." />

      <section className="export-hero">
        <div className="export-readiness">
          <div className={`readiness-ring ${ready ? 'ready' : ''}`} style={{ '--score': `${progress * 3.6}deg` } as React.CSSProperties}><div><strong>{progress}<sup>%</sup></strong><span>freigegeben</span></div></div>
          <div><span className="eyebrow">Submission Readiness</span><h2>{ready ? 'Bereit zur Übergabe.' : 'Fast bereit für den Kunden.'}</h2><p>{approved.length} von {analysis.questions.length} Antworten sind geprüft. {blocked.length} blockierte und {open.length} offene Antworten benötigen noch deine Entscheidung.</p></div>
        </div>
        <div className="export-card">
          <div className="export-file-icon"><FileSpreadsheet size={26} /></div>
          <div><strong>{evidencePackFilename(analysis.customer, capabilities.mode === 'api' ? 'xlsx' : 'csv')}</strong><span>{analysis.questions.length} Antworten · {analysis.documents.length} Quellen · {analysis.findings.length} Findings</span></div>
          <button className="button button-primary" onClick={onExport} disabled={exporting}>{exporting ? 'Wird erstellt …' : <><Download size={16} /> {capabilities.mode === 'api' ? 'XLSX exportieren' : 'Demo-CSV exportieren'}</>}</button>
          <small><LockKeyhole size={12} /> Blockierte Antworten werden als „Klärung erforderlich“ markiert.</small>
        </div>
      </section>

      <section className="review-status-grid">
        <button className="review-status-card approved" onClick={() => onNavigate('questions')}><span><CheckCircle2 size={19} /></span><div><strong>{approved.length}</strong><small>Freigegeben</small></div><ArrowRight size={16} /></button>
        <button className="review-status-card open" onClick={() => onNavigate('questions')}><span><AlertTriangle size={19} /></span><div><strong>{open.length}</strong><small>Review offen</small></div><ArrowRight size={16} /></button>
        <button className="review-status-card blocked" onClick={() => onNavigate('questions')}><span><XCircle size={19} /></span><div><strong>{blocked.length}</strong><small>Blockiert</small></div><ArrowRight size={16} /></button>
        <div className="review-status-card evidence"><span><ShieldCheck size={19} /></span><div><strong>100 %</strong><small>Zitate validiert</small></div><Check size={16} /></div>
      </section>

      <section className="review-layout">
        <article className="panel blockers-panel">
          <div className="panel-heading"><div><span className="eyebrow">Action required</span><h2>Blockierte Antworten</h2></div><span className="count-bubble">{blocked.length}</span></div>
          <div className="blocker-list">
            {blocked.map((question) => (
              <button key={question.id} onClick={() => onOpenQuestion(question)}>
                <span className="blocker-icon"><XCircle size={17} /></span>
                <span><small>{question.id} · {question.category}</small><strong>{question.text}</strong><em>{question.clarification}</em></span>
                <AnswerBadge status={question.status} compact />
                <ArrowRight size={15} />
              </button>
            ))}
          </div>
        </article>

        <article className="panel pack-panel">
          <span className="eyebrow">Evidence Pack</span><h2>Was exportiert wird</h2>
          <div className="pack-list">
            <div><span><FileCheck2 size={17} /></span><div><strong>Antwort-Matrix</strong><small>Status, Freigabe und Quellenreferenz</small></div><Check size={15} /></div>
            <div><span><ShieldCheck size={17} /></span><div><strong>IAM Findings</strong><small>Regel, Objekt und Handlungsempfehlung</small></div><Check size={15} /></div>
            <div><span><LockKeyhole size={17} /></span><div><strong>Review-Protokoll</strong><small>Offene und blockierte Entscheidungen</small></div><Check size={15} /></div>
          </div>
          <div className="synthetic-warning"><AlertTriangle size={16} /><span><strong>Nur Demonstrationsdaten.</strong> Alle Inhalte und Unternehmen sind synthetisch.</span></div>
        </article>
      </section>
    </div>
  );
}
