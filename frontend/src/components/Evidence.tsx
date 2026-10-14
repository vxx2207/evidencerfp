import {
  BookOpenCheck,
  CalendarDays,
  Check,
  ChevronRight,
  FileText,
  Fingerprint,
  Layers3,
  Search,
  ShieldCheck,
  X,
} from 'lucide-react';
import { useMemo, useState } from 'react';
import type { Analysis, EvidenceDocument } from '../types';
import { PageIntro } from './Chrome';

function formatDate(date: string) {
  return new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(date));
}

export function Evidence({ analysis }: { analysis: Analysis }) {
  const [query, setQuery] = useState('');
  const [activeDocument, setActiveDocument] = useState<EvidenceDocument | null>(null);
  const documents = useMemo(() => analysis.documents.filter((document) => `${document.title} ${document.summary} ${document.tags.join(' ')}`.toLowerCase().includes(query.toLowerCase())), [analysis.documents, query]);
  const totalSections = analysis.documents.reduce((sum, document) => sum + document.sections.length, 0);

  return (
    <div className="page evidence-page">
      <PageIntro
        eyebrow="Ground truth"
        title="Evidence Library"
        description="Die kuratierte Wissensbasis hinter jeder Antwort. Alle Dokumente, Versionen und Passagen sind vollständig synthetisch."
        action={<label className="library-search"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Dokumente durchsuchen …" /></label>}
      />

      <section className="library-stats">
        <div><FileText size={18} /><span><strong>{analysis.documents.length}</strong><small>Dokumente</small></span></div>
        <div><Layers3 size={18} /><span><strong>{totalSections}</strong><small>Indexierte Abschnitte</small></span></div>
        <div><Fingerprint size={18} /><span><strong>100 %</strong><small>Zitate validiert</small></span></div>
        <div><ShieldCheck size={18} /><span><strong>0</strong><small>Externe Datenquellen</small></span></div>
      </section>

      <section className="document-grid">
        {documents.map((document, index) => {
          const usage = analysis.questions.filter((question) => question.citations.some((citation) => citation.documentId === document.id)).length;
          return (
            <button key={document.id} className="document-card" onClick={() => setActiveDocument(document)}>
              <div className={`document-cover cover-${index + 1}`}>
                <span className="doc-brand">CERTA<span>ACCESS</span></span>
                <FileText size={31} />
                <strong>{document.title}</strong>
                <small>{document.version}</small>
                <i>CONFIDENTIAL · SYNTHETIC</i>
              </div>
              <div className="document-card-body">
                <div><span className="file-type"><FileText size={12} /> {document.type}</span><span className="verified-label"><Check size={12} /> verifiziert</span></div>
                <h2>{document.title}</h2>
                <p>{document.summary}</p>
                <div className="tag-row">{document.tags.map((tag) => <span key={tag}>{tag}</span>)}</div>
                <div className="document-stats"><span><BookOpenCheck size={13} /> {document.sections.length} Abschnitte</span><span>{usage} Zitate</span><ChevronRight size={15} /></div>
              </div>
            </button>
          );
        })}
      </section>

      {activeDocument && (
        <div className="drawer-shell" role="dialog" aria-modal="true" aria-labelledby="document-title">
          <button className="drawer-backdrop" onClick={() => setActiveDocument(null)} aria-label="Dokument schließen" />
          <aside className="citation-drawer library-drawer">
            <div className="drawer-header"><div><span className="eyebrow">Evidence Document</span><h2 id="document-title">{activeDocument.title}</h2></div><button className="icon-button" onClick={() => setActiveDocument(null)} aria-label="Schließen"><X size={19} /></button></div>
            <div className="document-overview"><div className="doc-icon-large"><FileText size={26} /></div><div><strong>{activeDocument.version} · {activeDocument.type}</strong><span>{activeDocument.size} · {activeDocument.owner}</span></div><span className="verified-label"><Check size={12} /> Indexiert</span></div>
            <div className="doc-date"><CalendarDays size={15} /> Veröffentlicht am {formatDate(activeDocument.publishedAt)}</div>
            <p className="drawer-summary">{activeDocument.summary}</p>
            <div className="evidence-divider"><span>INDEXIERTE ABSCHNITTE</span><i /></div>
            <div className="section-list">
              {activeDocument.sections.map((section) => (
                <article key={section.id}><div><span>{section.title}</span><small>Seite {section.page} · {section.id}</small></div><p>{section.content}</p></article>
              ))}
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}
