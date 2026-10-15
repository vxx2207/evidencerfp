import { ArrowRight, Check, ChevronDown, Filter, ListFilter, Search, SlidersHorizontal } from 'lucide-react';
import { useMemo, useState } from 'react';
import type { Analysis, AnswerStatus, Question } from '../types';
import { formatAnalysisDateTime } from '../format';
import { PageIntro } from './Chrome';
import { AnswerBadge, ReviewBadge } from './StatusBadge';

type StatusFilter = 'ALL' | AnswerStatus;

const tabs: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: 'Alle' },
  { value: 'SUPPORTED', label: 'Belegt' },
  { value: 'PARTIAL', label: 'Teilbelegt' },
  { value: 'NOT_FOUND', label: 'Nicht gefunden' },
];

export function Questions({ analysis, onOpenQuestion }: { analysis: Analysis; onOpenQuestion: (question: Question) => void }) {
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [category, setCategory] = useState('Alle Kategorien');
  const [onlyOpen, setOnlyOpen] = useState(false);
  const categories = ['Alle Kategorien', ...new Set(analysis.questions.map((q) => q.category))];

  const questions = useMemo(() => analysis.questions.filter((question) => {
    const matchesSearch = `${question.id} ${question.text} ${question.category}`.toLowerCase().includes(query.toLowerCase());
    const matchesStatus = status === 'ALL' || question.status === status;
    const matchesCategory = category === 'Alle Kategorien' || question.category === category;
    const matchesReview = !onlyOpen || question.reviewStatus !== 'FREIGEGEBEN';
    return matchesSearch && matchesStatus && matchesCategory && matchesReview;
  }), [analysis.questions, category, onlyOpen, query, status]);

  const countFor = (filter: StatusFilter) => filter === 'ALL' ? analysis.questions.length : analysis.questions.filter((q) => q.status === filter).length;

  return (
    <div className="page questions-page">
      <PageIntro
        eyebrow="RFP Workspace"
        title="Frage-Queue"
        description={`${analysis.questions.length} Antworten, priorisiert nach Evidenzlage und Freigabestatus. Jede Aussage bleibt bis zur Quelle rückverfolgbar.`}
        action={<button className="button button-primary" onClick={() => onOpenQuestion(analysis.questions.find((q) => q.reviewStatus !== 'FREIGEGEBEN') ?? analysis.questions[0])}>Nächstes Review <ArrowRight size={16} /></button>}
      />

      <section className="panel queue-panel">
        <div className="queue-toolbar">
          <div className="status-tabs" role="tablist" aria-label="Antwortstatus">
            {tabs.map((tab) => <button key={tab.value} role="tab" aria-selected={status === tab.value} className={status === tab.value ? 'active' : ''} onClick={() => setStatus(tab.value)}>{tab.label}<span>{countFor(tab.value)}</span></button>)}
          </div>
          <div className="queue-actions">
            <label className="queue-search"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Frage durchsuchen …" /></label>
            <label className="select-control"><Filter size={15} /><select value={category} onChange={(event) => setCategory(event.target.value)}>{categories.map((item) => <option key={item}>{item}</option>)}</select><ChevronDown size={14} /></label>
            <button className={`filter-toggle ${onlyOpen ? 'active' : ''}`} onClick={() => setOnlyOpen((value) => !value)}><SlidersHorizontal size={15} /> Nur offen {onlyOpen && <Check size={13} />}</button>
          </div>
        </div>

        <div className="table-wrap">
          <table className="question-table">
            <thead><tr><th>Frage</th><th>Kategorie</th><th>Antwortstatus</th><th>Review</th><th>Quellen</th><th aria-label="Öffnen" /></tr></thead>
            <tbody>
              {questions.map((question) => (
                <tr key={question.id} onClick={() => onOpenQuestion(question)}>
                  <td><span className="table-question-id">{question.id}</span><strong>{question.text}</strong></td>
                  <td><span className="category-chip">{question.category}</span></td>
                  <td><AnswerBadge status={question.status} /></td>
                  <td><ReviewBadge status={question.reviewStatus} /></td>
                  <td><span className="citation-count">{question.citations.length || '—'}</span></td>
                  <td><button className="row-open" aria-label={`${question.id} öffnen`}><ArrowRight size={16} /></button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {questions.length === 0 && <div className="empty-filter"><ListFilter size={24} /><strong>Keine Treffer</strong><span>Probiere einen anderen Filter oder Suchbegriff.</span></div>}
        <div className="table-footer"><span>{questions.length} von {analysis.questions.length} Fragen</span><span>Analysiert: {formatAnalysisDateTime(analysis.createdAt)}</span></div>
      </section>
    </div>
  );
}
