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
  Play,
  RotateCcw,
  TrendingUp,
} from 'lucide-react';
import type { Analysis, AppView, Question } from '../types';
import { isReviewDecided, isReviewOpen } from '../review';
import { AnswerBadge } from './StatusBadge';

function formatDate(value: string) {
  return new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}

export function Dashboard({ analysis, onNavigate, onOpenQuestion, onImport, onStartTour, onResetDemo }: { analysis: Analysis; onNavigate: (view: AppView) => void; onOpenQuestion: (question: Question) => void; onImport?: () => void; onStartTour?: () => void; onResetDemo?: () => void }) {
  const counts = {
    supported: analysis.questions.filter((q) => q.status === 'SUPPORTED').length,
    partial: analysis.questions.filter((q) => q.status === 'PARTIAL').length,
    missing: analysis.questions.filter((q) => q.status === 'NOT_FOUND').length,
    approved: analysis.questions.filter((q) => q.reviewStatus === 'FREIGEGEBEN').length,
    blocked: analysis.questions.filter((q) => q.reviewStatus === 'BLOCKIERT').length,
    decided: analysis.questions.filter((q) => isReviewDecided(q.reviewStatus)).length,
  };
  const severityCounts = {
    critical: analysis.findings.filter((finding) => finding.severity === 'KRITISCH').length,
    high: analysis.findings.filter((finding) => finding.severity === 'HOCH').length,
    medium: analysis.findings.filter((finding) => finding.severity === 'MITTEL').length,
  };
  const totalSections = analysis.documents.reduce((sum, document) => sum + document.sections.length, 0);
  const openDecisions = analysis.questions.filter((question) => isReviewOpen(question.reviewStatus)).length;
  const sourceCoverage = Math.round(((counts.supported + counts.partial) / analysis.questions.length) * 100);
  const priorityQuestions = analysis.questions.filter((question) => isReviewOpen(question.reviewStatus)).slice(0, 4);
  const nextOpenQuestion = priorityQuestions[0];

  return (
    <div className="page dashboard-page">
      <section className="hero-panel">
        <div className="hero-copy">
          <div className="hero-kicker"><span>Analyse abgeschlossen</span><span>•</span><span>{formatDate(analysis.createdAt)}</span></div>
          <h1>Von {analysis.questions.length} Fragen zu<br /><em>belastbaren Antworten.</em></h1>
          <p>EvidenceRFP verknüpft jede Aussage mit prüfbarer Evidenz und deckt Identitätsrisiken auf – bevor dein Angebot den Kunden erreicht.</p>
          <div className="hero-actions">
            {onStartTour && <button className="button button-tour" onClick={onStartTour}><Play size={16} fill="currentColor" /> 60-Sekunden-Demo starten</button>}
            <button className="button button-ghost" onClick={() => nextOpenQuestion ? onOpenQuestion(nextOpenQuestion) : onNavigate('review')}>{nextOpenQuestion ? 'Review fortsetzen' : 'Zum Export'} <ArrowRight size={17} /></button>
            <button className="button button-ghost" onClick={() => onNavigate('evidence')}><BookOpenCheck size={17} /> Quellen ansehen</button>
            {onImport && <button className="button button-ghost" onClick={onImport}><Upload size={17} /> Neue Analyse</button>}
            {onResetDemo && <button className="button button-reset-demo" onClick={onResetDemo}><RotateCcw size={15} /> Demo zurücksetzen</button>}
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
          <div><span>IAM Findings</span><strong>{analysis.findings.length}</strong><small>{severityCounts.critical} kritisch · {severityCounts.high} hoch · {severityCounts.medium} mittel</small></div>
          <span className="metric-trend">Tenant</span>
        </article>
      </section>

      <details className="panel business-impact-panel">
        <summary>
          <span className="business-impact-heading"><span className="insight-icon"><TrendingUp size={18} /></span><span><span className="eyebrow">Synthetischer ROI-Case</span><strong>Business Impact als transparente Beispielannahme</strong></span></span>
          <span className="business-impact-metrics">
            <span><strong>35 h</strong><small>pro Monat</small></span>
            <span><strong>~ 7</strong><small>Monate Amortisation</small></span>
            <span><strong>38,2 %</strong><small>Beispiel-ROI im 1. Jahr</small></span>
          </span>
          <span className="business-impact-more">Annahmen anzeigen <ArrowRight size={14} /></span>
        </summary>
        <div className="business-impact-details">
          <p><strong>Reines Rechenbeispiel – kein Kundenergebnis:</strong> 35 eingesparte Stunden pro Monat × 75 € interner Stundensatz = 2.625 € pro Monat beziehungsweise 31.500 € Bruttonutzen pro Jahr.</p>
          <p>12.000 € Einführung plus 900 € Betrieb pro Monat ergeben 22.800 € Kosten im ersten Jahr. Bei 1.725 € Monatsnetto liegt die Amortisation bei 6,96 Monaten; der Beispiel-ROI beträgt <strong>(31.500 € − 22.800 €) ÷ 22.800 € = 38,2 %</strong>.</p>
        </div>
      </details>

      <section className="dashboard-grid">
        <article className="panel priority-panel">
          <div className="panel-heading">
            <div><span className="eyebrow">Human in the loop</span><h2>Jetzt deine Entscheidung</h2></div>
            <button className="text-button" onClick={() => onNavigate('questions')}>Alle {analysis.questions.length} Fragen <ArrowRight size={14} /></button>
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
            {priorityQuestions.length === 0 && <div className="priority-empty"><CheckCircle2 size={19} /><span><strong>Alle Antworten sind entschieden.</strong><small>{counts.approved} freigegeben · {counts.blocked} als Klärung blockiert</small></span></div>}
          </div>
        </article>

        <article className="panel pipeline-panel">
          <div className="panel-heading">
            <div><span className="eyebrow">Evidence Pipeline</span><h2>Nachvollziehbar. Schritt für Schritt.</h2></div>
            <span className="pipeline-time"><Clock3 size={14} /> {analysis.status === 'COMPLETED' ? 'Abgeschlossen' : `${analysis.progress} %`}</span>
          </div>
          <div className="pipeline">
            <div className="pipeline-step done"><span><FileSearch size={17} /></span><div><strong>Import</strong><small>{analysis.questions.length} Fragen</small></div><i /></div>
            <div className="pipeline-step done"><span><BookOpenCheck size={17} /></span><div><strong>Indexierung</strong><small>{totalSections} Passagen</small></div><i /></div>
            <div className="pipeline-step done"><span><LockKeyhole size={17} /></span><div><strong>IAM Scan</strong><small>{analysis.findings.length} Findings</small></div><i /></div>
            <div className="pipeline-step done"><span><Sparkles size={17} /></span><div><strong>Synthese</strong><small>{analysis.questions.length} Entwürfe</small></div><i /></div>
            <div className={`pipeline-step ${openDecisions === 0 ? 'done' : 'current'}`}><span><Gauge size={17} /></span><div><strong>Review</strong><small>{counts.decided} entschieden</small></div></div>
          </div>
        </article>

        <article className="panel sales-panel">
          <div className="insight-icon"><Lightbulb size={19} /></div>
          <span className="eyebrow">Presales Signal</span>
          <h2>{counts.missing > 0 ? 'Wissen, wo du nachfragen musst.' : 'Alle Fragen sind durch Evidenz abgedeckt.'}</h2>
          <p>{counts.missing > 0 ? `${counts.missing} unbelegte ${counts.missing === 1 ? 'Frage ist' : 'Fragen sind'} keine Schwäche – ${counts.missing === 1 ? 'sie ist' : 'sie sind'} deine Agenda für den nächsten Kundentermin.` : 'Es wurden keine unbelegten Fragen erkannt. Der nächste Schritt ist die fachliche Freigabe.'}</p>
          <button className="text-button" onClick={() => onNavigate('questions')}>Klärungsfragen öffnen <ArrowRight size={14} /></button>
        </article>

        <article className="panel readiness-panel">
          <div className="readiness-top"><span className="eyebrow">Submission Readiness</span><span>{counts.decided}/{analysis.questions.length}</span></div>
          <div className="readiness-track"><i style={{ width: `${Math.round((counts.decided / analysis.questions.length) * 100)}%` }} /></div>
          <div className="readiness-copy">{openDecisions > 0 ? <TriangleAlert size={16} /> : <CheckCircle2 size={16} />}<span><strong>{openDecisions > 0 ? `${openDecisions} Entscheidungen offen.` : 'Alle Antworten entschieden.'}</strong> Blockierte Antworten werden als Klärung gekennzeichnet.</span></div>
          <button className="button button-dark" onClick={() => onNavigate('review')}>Zum Review <ArrowRight size={16} /></button>
        </article>
      </section>
    </div>
  );
}
