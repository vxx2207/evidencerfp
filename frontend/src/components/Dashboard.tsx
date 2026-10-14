import {
  ArrowRight,
  BadgeCheck,
  BookOpenCheck,
  CheckCircle2,
  CircleEllipsis,
  Clock3,
  FileSearch,
  Gauge,
  Lightbulb,
  LockKeyhole,
  ShieldCheck,
  Sparkles,
  TriangleAlert,
  Upload,
} from 'lucide-react';
import type { Analysis, AppView, Question } from '../types';
import { AnswerBadge } from './StatusBadge';

function formatDate(value: string) {
  return new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}

export function Dashboard({ analysis, onNavigate, onOpenQuestion, onImport }: { analysis: Analysis; onNavigate: (view: AppView) => void; onOpenQuestion: (question: Question) => void; onImport?: () => void }) {
  const counts = {
    supported: analysis.questions.filter((q) => q.status === 'SUPPORTED').length,
    partial: analysis.questions.filter((q) => q.status === 'PARTIAL').length,
    missing: analysis.questions.filter((q) => q.status === 'NOT_FOUND').length,
    approved: analysis.questions.filter((q) => q.reviewStatus === 'FREIGEGEBEN').length,
  };
  const sourceCoverage = Math.round(((counts.supported + counts.partial) / analysis.questions.length) * 100);
  const priorityQuestions = analysis.questions.filter((q) => q.reviewStatus === 'BLOCKIERT' || q.status === 'PARTIAL').slice(0, 4);

  return (
    <div className="page dashboard-page">
      <section className="hero-panel">
        <div className="hero-copy">
          <div className="hero-kicker"><span>Analyse abgeschlossen</span><span>•</span><span>{formatDate(analysis.createdAt)}</span></div>
          <h1>Von {analysis.questions.length} Fragen zu<br /><em>belastbaren Antworten.</em></h1>
          <p>EvidenceRFP verknüpft jede Aussage mit prüfbarer Evidenz und deckt Identitätsrisiken auf – bevor dein Angebot den Kunden erreicht.</p>
          <div className="hero-actions">
            <button className="button button-primary" onClick={() => onNavigate('workbench')}>Review starten <ArrowRight size={17} /></button>
            <button className="button button-ghost" onClick={() => onNavigate('evidence')}><BookOpenCheck size={17} /> Quellen ansehen</button>
            {onImport && <button className="button button-ghost" onClick={onImport}><Upload size={17} /> Neue Analyse</button>}
          </div>
        </div>
        <div className="hero-score">
          <div className="score-orbit">
            <div className="score-ring" style={{ '--score': `${sourceCoverage * 3.6}deg` } as React.CSSProperties}>
              <div><strong>{sourceCoverage}<sup>%</sup></strong><span>Evidenzabdeckung</span></div>
            </div>
            <span className="orbit-node node-one"><ShieldCheck size={14} /></span>
            <span className="orbit-node node-two"><BadgeCheck size={14} /></span>
            <span className="orbit-node node-three"><LockKeyhole size={14} /></span>
          </div>
          <div className="score-caption"><span className="pulse-dot" /><span><strong>{counts.supported} von {analysis.questions.length}</strong> vollständig belegt</span></div>
        </div>
        <div className="hero-watermark">EVIDENCE<br />FIRST</div>
      </section>

      <section className="metric-grid" aria-label="Analysekennzahlen">
        <article className="metric-card metric-cyan">
          <div className="metric-icon"><CheckCircle2 size={19} /></div>
          <div><span>Vollständig belegt</span><strong>{counts.supported}</strong><small>Direkt aus Quellen ableitbar</small></div>
          <div className="mini-bars">{[31,43,36,52,59,71,78].map((height, i) => <i key={i} style={{ height }} />)}</div>
        </article>
        <article className="metric-card metric-amber">
          <div className="metric-icon"><CircleEllipsis size={19} /></div>
          <div><span>Teilbelegt</span><strong>{counts.partial}</strong><small>Benötigen fachliche Klärung</small></div>
          <span className="metric-trend">Review</span>
        </article>
        <article className="metric-card metric-red">
          <div className="metric-icon"><FileSearch size={19} /></div>
          <div><span>Nicht gefunden</span><strong>{counts.missing}</strong><small>Keine belastbare Evidenz</small></div>
          <span className="metric-trend">Blockiert</span>
        </article>
        <article className="metric-card metric-violet">
          <div className="metric-icon"><ShieldCheck size={19} /></div>
          <div><span>IAM Findings</span><strong>{analysis.findings.length}</strong><small>1 kritisch · 1 hoch · 1 mittel</small></div>
          <span className="metric-trend">Tenant</span>
        </article>
      </section>

      <section className="dashboard-grid">
        <article className="panel priority-panel">
          <div className="panel-heading">
            <div><span className="eyebrow">Human in the loop</span><h2>Jetzt deine Entscheidung</h2></div>
            <button className="text-button" onClick={() => onNavigate('questions')}>Alle 24 Fragen <ArrowRight size={14} /></button>
          </div>
          <div className="priority-list">
            {priorityQuestions.map((question) => (
              <button key={question.id} onClick={() => onOpenQuestion(question)} className="priority-row">
                <span className={`priority-indicator status-${question.status.toLowerCase()}`} />
                <span className="question-id">{question.id}</span>
                <span className="priority-question"><strong>{question.text}</strong><small>{question.category} · {question.updatedAt}</small></span>
                <AnswerBadge status={question.status} />
                <ArrowRight size={16} className="row-arrow" />
              </button>
            ))}
          </div>
        </article>

        <article className="panel pipeline-panel">
          <div className="panel-heading">
            <div><span className="eyebrow">Evidence Pipeline</span><h2>Nachvollziehbar. Schritt für Schritt.</h2></div>
            <span className="pipeline-time"><Clock3 size={14} /> 01:47 min</span>
          </div>
          <div className="pipeline">
            <div className="pipeline-step done"><span><FileSearch size={17} /></span><div><strong>Import</strong><small>24 Fragen</small></div><i /></div>
            <div className="pipeline-step done"><span><BookOpenCheck size={17} /></span><div><strong>Indexierung</strong><small>58 Chunks</small></div><i /></div>
            <div className="pipeline-step done"><span><LockKeyhole size={17} /></span><div><strong>IAM Scan</strong><small>3 Findings</small></div><i /></div>
            <div className="pipeline-step done"><span><Sparkles size={17} /></span><div><strong>Synthese</strong><small>24 Entwürfe</small></div><i /></div>
            <div className="pipeline-step current"><span><Gauge size={17} /></span><div><strong>Review</strong><small>{counts.approved} freigegeben</small></div></div>
          </div>
        </article>

        <article className="panel sales-panel">
          <div className="insight-icon"><Lightbulb size={19} /></div>
          <span className="eyebrow">Presales Signal</span>
          <h2>Wissen, wo du nachfragen musst.</h2>
          <p>Die drei unbelegten Fragen sind keine Schwäche – sie sind deine Agenda für den nächsten Kundentermin.</p>
          <button className="text-button" onClick={() => onNavigate('questions')}>Klärungsfragen öffnen <ArrowRight size={14} /></button>
        </article>

        <article className="panel readiness-panel">
          <div className="readiness-top"><span className="eyebrow">Submission Readiness</span><span>{counts.approved}/{analysis.questions.length}</span></div>
          <div className="readiness-track"><i style={{ width: `${Math.round((counts.approved / analysis.questions.length) * 100)}%` }} /></div>
          <div className="readiness-copy"><TriangleAlert size={16} /><span><strong>{analysis.questions.length - counts.approved} Entscheidungen offen.</strong> Blockierte Antworten werden nicht exportiert.</span></div>
          <button className="button button-dark" onClick={() => onNavigate('review')}>Zum Review <ArrowRight size={16} /></button>
        </article>
      </section>
    </div>
  );
}
