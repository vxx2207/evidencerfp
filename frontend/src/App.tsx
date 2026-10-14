import { AlertTriangle, RefreshCcw, ShieldCheck } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Dashboard } from './components/Dashboard';
import { Evidence } from './components/Evidence';
import { Findings } from './components/Findings';
import { GlobalSearch, Sidebar, Toast, Topbar } from './components/Chrome';
import { Questions } from './components/Questions';
import { ReviewExport } from './components/ReviewExport';
import { Workbench } from './components/Workbench';
import { ImportAnalysis } from './components/ImportAnalysis';
import { createAnalysisGateway } from './gateways';
import type { Analysis, AppView, Capabilities, Question, QuestionUpdate } from './types';
import './styles.css';

const pageTitles: Record<AppView, string> = {
  dashboard: 'Übersicht',
  questions: 'Frage-Queue',
  workbench: 'Answer Workbench',
  findings: 'IAM Findings',
  evidence: 'Evidence Library',
  review: 'Review & Export',
};

function LoadingScreen() {
  return (
    <div className="loading-screen">
      <div className="loading-brand"><span>ER</span></div>
      <h1>EvidenceRFP</h1>
      <p>Verifizierte Evidenz wird vorbereitet …</p>
      <div className="loading-line"><i /></div>
      <small><ShieldCheck size={14} /> Synthetic workspace · CertaAccess</small>
    </div>
  );
}

export default function App() {
  const gateway = useMemo(() => createAnalysisGateway(), []);
  const [analysis, setAnalysis] = useState<Analysis | null>(null);
  const [capabilities, setCapabilities] = useState<Capabilities | null>(null);
  const [view, setView] = useState<AppView>('dashboard');
  const [activeQuestionId, setActiveQuestionId] = useState('IAM-001');
  const [mobileOpen, setMobileOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [importOpen, setImportOpen] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [nextCapabilities, nextAnalysis] = await Promise.all([gateway.getCapabilities(), gateway.getAnalysis()]);
      setCapabilities(nextCapabilities);
      setAnalysis(nextAnalysis);
      setActiveQuestionId(nextAnalysis.questions[0]?.id ?? '');
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Die Analyse konnte nicht geladen werden.');
    }
  }, [gateway]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setSearchOpen(true);
      }
      if (event.key === 'Escape') setSearchOpen(false);
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timeout = window.setTimeout(() => setToast(null), 4200);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  function openQuestion(question: Question) {
    setActiveQuestionId(question.id);
    setView('workbench');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function updateQuestion(questionId: string, update: QuestionUpdate) {
    if (!analysis) return;
    try {
      const updated = await gateway.updateQuestion(analysis.id, questionId, update);
      setAnalysis((current) => current ? { ...current, questions: current.questions.map((item) => item.id === questionId ? updated : item) } : current);
      setToast(update.reviewStatus === 'FREIGEGEBEN' ? 'Antwort wurde freigegeben.' : update.reviewStatus === 'BLOCKIERT' ? 'Antwort wurde blockiert.' : 'Antwortentwurf gespeichert.');
    } catch (reason) {
      setToast(reason instanceof Error ? reason.message : 'Änderung konnte nicht gespeichert werden.');
      throw reason;
    }
  }

  async function exportAnalysis() {
    if (!analysis || !capabilities) return;
    setExporting(true);
    try {
      const blob = await gateway.exportAnalysis(analysis);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `Nordstern_Evidence_Pack.${capabilities.mode === 'api' ? 'xlsx' : 'csv'}`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      setToast('Evidence Pack wurde erstellt.');
    } catch (reason) {
      setToast(reason instanceof Error ? reason.message : 'Export konnte nicht erstellt werden.');
    } finally {
      setExporting(false);
    }
  }

  async function importAnalysis(input: Parameters<typeof gateway.createAnalysis>[0]) {
    const imported = await gateway.createAnalysis(input);
    setAnalysis(imported);
    setActiveQuestionId(imported.questions[0]?.id ?? '');
    setView('dashboard');
    setImportOpen(false);
    setToast(`Analyse für ${imported.customer} wurde abgeschlossen.`);
  }

  if (error) {
    return <div className="error-screen"><div><AlertTriangle size={28} /><h1>Workspace nicht erreichbar</h1><p>{error}</p><button className="button button-primary" onClick={() => void load()}><RefreshCcw size={16} /> Erneut versuchen</button></div></div>;
  }
  if (!analysis || !capabilities) return <LoadingScreen />;

  const activeQuestion = analysis.questions.find((item) => item.id === activeQuestionId) ?? analysis.questions[0];
  const openQuestions = analysis.questions.filter((question) => question.reviewStatus !== 'FREIGEGEBEN').length;

  return (
    <div className="app-shell">
      <Sidebar view={view} onNavigate={setView} mobileOpen={mobileOpen} onMobileClose={() => setMobileOpen(false)} openQuestions={openQuestions} findings={analysis.findings.length} />
      <div className="app-main">
        <Topbar pageTitle={pageTitles[view]} customer={analysis.customer} mode={capabilities.mode} onMenu={() => setMobileOpen(true)} onSearch={() => setSearchOpen(true)} />
        <div className="page-host">
          {view === 'dashboard' && <Dashboard analysis={analysis} onNavigate={setView} onOpenQuestion={openQuestion} onImport={capabilities.uploadsEnabled ? () => setImportOpen(true) : undefined} />}
          {view === 'questions' && <Questions analysis={analysis} onOpenQuestion={openQuestion} />}
          {view === 'workbench' && activeQuestion && <Workbench analysis={analysis} activeQuestion={activeQuestion} onSelectQuestion={(question) => setActiveQuestionId(question.id)} onUpdateQuestion={updateQuestion} />}
          {view === 'findings' && <Findings analysis={analysis} />}
          {view === 'evidence' && <Evidence analysis={analysis} />}
          {view === 'review' && <ReviewExport analysis={analysis} capabilities={capabilities} onOpenQuestion={openQuestion} onNavigate={setView} onExport={() => void exportAnalysis()} exporting={exporting} />}
        </div>
      </div>
      <GlobalSearch open={searchOpen} onClose={() => setSearchOpen(false)} onNavigate={setView} />
      <Toast message={toast} onClose={() => setToast(null)} />
      <ImportAnalysis open={importOpen} onClose={() => setImportOpen(false)} onSubmit={importAnalysis} />
      <span className="global-demo-ribbon">SYNTHETISCHE DATEN · PORTFOLIO DEMO</span>
    </div>
  );
}
