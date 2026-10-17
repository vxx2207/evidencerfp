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
import { isReviewDecided, reviewDecisionProgress } from '../review';
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
  const decided = analysis.questions.filter((q) => isReviewDecided(q.reviewStatus));
  const ready = analysis.questions.length > 0 && open.length === 0;
  const progress = reviewDecisionProgress(analysis.questions);
  const supportsXlsx = capabilities.exportFormats.includes('XLSX');

  return (
    <div className="page review-page">
      <PageIntro eyebrow="Final Gate" title="Review & Export" description="Freigabestatus prüfen, offene Punkte auflösen und das Evidence Pack für den Kunden vorbereiten." />

      <section className="export-hero">
        <div className="export-readiness">
          <div className={`readiness-ring ${ready ? 'ready' : ''}`} style={{ '--score': `${progress * 3.6}deg` } as React.CSSProperties}><div><strong>{progress}<sup>%</sup></strong><span>entschieden</span></div></div>
          <div><span className="eyebrow">Submission Readiness</span><h2>{ready ? 'Review abgeschlossen.' : 'Noch Entscheidungen offen.'}</h2><p>{decided.length} von {analysis.questions.length} Antworten sind entschieden: {approved.length} freigegeben und {blocked.length} als Klärung blockiert. {open.length > 0 ? `${open.length} ${open.length === 1 ? 'Antwort ist' : 'Antworten sind'} noch offen.` : 'Das Evidence Pack kann erstellt werden.'}</p></div>
        </div>
        <div className="export-card">
          <div className="export-file-icon"><FileSpreadsheet size={26} /></div>
          <div><strong>{evidencePackFilename(analysis.customer, supportsXlsx ? 'xlsx' : 'csv')}</strong><span>{analysis.questions.length} Antworten · {analysis.documents.length} Quellen · {analysis.findings.length} Findings</span></div>
          <button className="button button-primary" onClick={onExport} disabled={exporting || !ready} title={!ready ? 'Vor dem Export müssen alle offenen Antworten entschieden werden.' : undefined}>{exporting ? 'Wird erstellt …' : <><Download size={16} /> {supportsXlsx ? 'XLSX exportieren' : 'CSV exportieren'}</>}</button>
          <small><LockKeyhole size={12} /> {ready ? 'Blockierte Antworten werden als „Klärung erforderlich“ dokumentiert.' : 'Export verfügbar, sobald keine Antwort mehr offen ist.'}</small>
        </div>
      </section>

      <section className="review-status-grid">
        <button className="review-status-card approved" onClick={() => onNavigate('questions')}><span><CheckCircle2 size={19} /></span><div><strong>{approved.length}</strong><small>Freigegeben</small></div><ArrowRight size={16} /></button>
        <button className="review-status-card open" onClick={() => onNavigate('questions')}><span><AlertTriangle size={19} /></span><div><strong>{open.length}</strong><small>Review offen</small></div><ArrowRight size={16} /></button>
        <button className="review-status-card blocked" onClick={() => onNavigate('questions')}><span><XCircle size={19} /></span><div><strong>{blocked.length}</strong><small>Klärung blockiert</small></div><ArrowRight size={16} /></button>
        <div className="review-status-card evidence"><span><ShieldCheck size={19} /></span><div><strong>100 %</strong><small>Zitate validiert</small></div><Check size={16} /></div>
      </section>

      <section className="review-layout">
        <article className="panel blockers-panel">
          <div className="panel-heading"><div><span className="eyebrow">Im Evidence Pack dokumentiert</span><h2>Bewusste Klärungen</h2></div><span className="count-bubble">{blocked.length}</span></div>
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
            <div><span><LockKeyhole size={17} /></span><div><strong>Review-Protokoll</strong><small>Freigaben und dokumentierte Klärungen</small></div><Check size={15} /></div>
          </div>
          <div className="synthetic-warning"><AlertTriangle size={16} /><span><strong>Nur Demonstrationsdaten.</strong> Alle Inhalte und Unternehmen sind synthetisch.</span></div>
        </article>
      </section>
    </div>
  );
}
