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
import { DemoTour, type DemoTourStep } from './components/DemoTour';
import { evidencePackFilename } from './format';
import { createAnalysisGateway } from './gateways';
import { isReviewOpen } from './review';
import type { Analysis, AnalysisProgress, AppView, Capabilities, Question, QuestionUpdate } from './types';
import './styles.css';

const pageTitles: Record<AppView, string> = {
  dashboard: 'Übersicht',
  questions: 'Frage-Queue',
  workbench: 'Answer Workbench',
  findings: 'IAM Findings',
  evidence: 'Evidence Library',
  review: 'Review & Export',
};

const tourHashes: Record<DemoTourStep, string> = {
  source: '#tour/source',
  conflict: '#tour/conflict',
  'not-found': '#tour/not-found',
  finding: '#tour/finding',
  export: '#tour/export',
};

function tourStepFromHash(hash: string): DemoTourStep | null {
  return (Object.entries(tourHashes).find(([, value]) => value === hash)?.[0] as DemoTourStep | undefined) ?? null;
}

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
  const [tourStep, setTourStep] = useState<DemoTourStep | null>(() => tourStepFromHash(window.location.hash));

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
    function onHashChange() {
      setTourStep(tourStepFromHash(window.location.hash));
    }
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  useEffect(() => {
    if (!analysis || !tourStep) return;
    if (tourStep === 'finding') {
      setView('findings');
    } else if (tourStep === 'export') {
      const reviewQuestion = analysis.questions.find((question) => question.id === 'IAM-004');
      if (reviewQuestion?.reviewStatus === 'OFFEN') {
        setActiveQuestionId(reviewQuestion.id);
        setView('workbench');
      } else {
        setView('review');
      }
    } else {
      const questionId = tourStep === 'source' ? 'IAM-001' : tourStep === 'conflict' ? 'IAM-020' : 'IAM-022';
      if (analysis.questions.some((question) => question.id === questionId)) setActiveQuestionId(questionId);
      setView('workbench');
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, [analysis, tourStep]);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setSearchOpen(true);
      }
      if (event.key === 'Escape') {
        setSearchOpen(false);
        if (tourStep) closeTour();
      }
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [tourStep]);

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
      const currentQuestion = analysis.questions.find((item) => item.id === questionId);
      const updated = await gateway.updateQuestion(analysis.id, questionId, {
        ...update,
        expectedRevision: currentQuestion?.revision,
      });
      setAnalysis((current) => current ? { ...current, questions: current.questions.map((item) => item.id === questionId ? updated : item) } : current);
      setToast(update.reviewStatus === 'FREIGEGEBEN' ? 'Antwort wurde freigegeben.' : update.reviewStatus === 'BLOCKIERT' ? 'Antwort wurde blockiert.' : 'Antwortentwurf gespeichert.');
      if (tourStep === 'export' && questionId === 'IAM-004' && update.reviewStatus && update.reviewStatus !== 'OFFEN') setView('review');
    } catch (reason) {
      setToast(reason instanceof Error ? reason.message : 'Änderung konnte nicht gespeichert werden.');
      throw reason;
    }
  }

  function navigateTour(step: DemoTourStep) {
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}${tourHashes[step]}`);
    setTourStep(step);
  }

  function closeTour() {
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}`);
    setTourStep(null);
  }

  async function resetDemo() {
    try {
      const resetAnalysis = await gateway.resetAnalysis?.();
      if (resetAnalysis) {
        setAnalysis(resetAnalysis);
        setActiveQuestionId(resetAnalysis.questions[0]?.id ?? '');
      } else {
        await load();
      }
      closeTour();
      setView('dashboard');
      setToast('Demo wurde auf den Ausgangszustand zurückgesetzt.');
    } catch (reason) {
      setToast(reason instanceof Error ? reason.message : 'Demo konnte nicht zurückgesetzt werden.');
    }
  }

  async function exportAnalysis() {
    if (!analysis || !capabilities) return;
    if (analysis.questions.some((question) => isReviewOpen(question.reviewStatus))) {
      setToast('Bitte entscheide zuerst alle offenen Antworten.');
      return;
    }
    setExporting(true);
    try {
      const blob = await gateway.exportAnalysis(analysis);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = evidencePackFilename(analysis.customer, capabilities.exportFormats.includes('XLSX') ? 'xlsx' : 'csv');
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

  async function importAnalysis(
    input: Parameters<typeof gateway.createAnalysis>[0],
    onProgress: (progress: AnalysisProgress) => void,
  ) {
    const imported = await gateway.createAnalysis(input, onProgress);
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
  const openQuestions = analysis.questions.filter((question) => isReviewOpen(question.reviewStatus)).length;

  return (
    <div className="app-shell">
      <Sidebar view={view} onNavigate={setView} mobileOpen={mobileOpen} onMobileClose={() => setMobileOpen(false)} openQuestions={openQuestions} findings={analysis.findings.length} />
      <div className="app-main">
        <Topbar pageTitle={pageTitles[view]} customer={analysis.customer} mode={capabilities.mode} onMenu={() => setMobileOpen(true)} onSearch={() => setSearchOpen(true)} />
        <div className="page-host">
          {view === 'dashboard' && <Dashboard analysis={analysis} onNavigate={setView} onOpenQuestion={openQuestion} onImport={capabilities.uploadsEnabled ? () => setImportOpen(true) : undefined} onStartTour={capabilities.mode === 'demo' ? () => navigateTour('source') : undefined} onResetDemo={capabilities.mode === 'demo' ? () => void resetDemo() : undefined} />}
          {view === 'questions' && <Questions analysis={analysis} onOpenQuestion={openQuestion} />}
          {view === 'workbench' && activeQuestion && <Workbench analysis={analysis} activeQuestion={activeQuestion} onSelectQuestion={(question) => setActiveQuestionId(question.id)} onUpdateQuestion={updateQuestion} tourCitationId={tourStep === 'source' ? 'cit-001' : undefined} />}
          {view === 'findings' && <Findings analysis={analysis} focusFindingId={tourStep === 'finding' ? 'finding-001' : undefined} />}
          {view === 'evidence' && <Evidence analysis={analysis} />}
          {view === 'review' && <ReviewExport analysis={analysis} capabilities={capabilities} onOpenQuestion={openQuestion} onNavigate={setView} onExport={() => void exportAnalysis()} exporting={exporting} />}
        </div>
      </div>
      <GlobalSearch analysis={analysis} open={searchOpen} onClose={() => setSearchOpen(false)} onNavigate={setView} onOpenQuestion={openQuestion} />
      <Toast message={toast} onClose={() => setToast(null)} />
      <ImportAnalysis open={importOpen} onClose={() => setImportOpen(false)} onSubmit={importAnalysis} />
      {capabilities.mode === 'demo' && tourStep && <DemoTour step={tourStep} view={view} openQuestions={openQuestions} onStep={navigateTour} onClose={closeTour} onReset={() => void resetDemo()} />}
      <span className="global-demo-ribbon">SYNTHETISCHE DATEN · PORTFOLIO DEMO</span>
    </div>
  );
}
