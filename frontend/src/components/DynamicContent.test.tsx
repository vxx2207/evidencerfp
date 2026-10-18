import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import fixture from '../data/analysis.json';
import type { Analysis, Capabilities } from '../types';
import { Dashboard } from './Dashboard';
import { Findings } from './Findings';
import { Questions } from './Questions';
import { ReviewExport } from './ReviewExport';

function compactAnalysis(): Analysis {
  const analysis = structuredClone(fixture) as Analysis;
  return {
    ...analysis,
    customer: 'Alpenwerke GmbH',
    questions: analysis.questions.slice(0, 2),
    documents: analysis.documents.slice(0, 1).map((document) => ({ ...document, sections: document.sections.slice(0, 2) })),
    findings: analysis.findings.slice(0, 1),
  };
}

describe('dynamische Analysedaten', () => {
  it('leitet Dashboard-Mengen aus der geladenen Analyse ab', () => {
    render(<Dashboard analysis={compactAnalysis()} onNavigate={vi.fn()} onOpenQuestion={vi.fn()} />);

    expect(screen.getByRole('heading', { name: /Von 2 Fragen zu/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Alle 2 Fragen/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Zum Export/i })).toBeInTheDocument();
    expect(screen.getByText('Alle Antworten sind entschieden.')).toBeInTheDocument();
    expect(screen.getByText('1 kritisch · 0 hoch · 0 mittel')).toBeInTheDocument();
    expect(screen.getByText('2 Passagen')).toBeInTheDocument();
    expect(screen.getByText('Wie dieses Ergebnis abgesichert wurde')).toBeInTheDocument();
    expect(screen.getByText('Pipelinephase')).toBeInTheDocument();
    expect(screen.getByText('Dense + FTS → RRF')).toBeInTheDocument();
  });

  it('verwendet Kunde, Analysezeit und Finding-Mengen statt Demo-Konstanten', () => {
    const analysis = compactAnalysis();
    const capabilities: Capabilities = { mode: 'api', uploadsEnabled: true, aiEnabled: false, exportFormats: ['XLSX'] };
    const review = render(<ReviewExport analysis={analysis} capabilities={capabilities} onOpenQuestion={vi.fn()} onNavigate={vi.fn()} onExport={vi.fn()} exporting={false} />);
    expect(screen.getByText('Alpenwerke_GmbH_Evidence_Pack.xlsx')).toBeInTheDocument();
    review.unmount();

    const questions = render(<Questions analysis={analysis} onOpenQuestion={vi.fn()} />);
    expect(screen.getByText(/2 Antworten, priorisiert/)).toBeInTheDocument();
    expect(screen.getByText(/Analysiert:/)).toBeInTheDocument();
    questions.unmount();

    render(<Findings analysis={analysis} />);
    expect(screen.getByText(/1 Befunde/)).toBeInTheDocument();
    expect(screen.getByText(/Alpenwerke GmbH-Tenant/)).toBeInTheDocument();
    expect(screen.queryByText(/2\.418 Identitäten/)).not.toBeInTheDocument();
  });

  it('wertet blockierte Antworten als entschiedene Klärung und erlaubt den Export', async () => {
    const user = userEvent.setup();
    const analysis = compactAnalysis();
    analysis.questions = [
      { ...analysis.questions[0], reviewStatus: 'FREIGEGEBEN' },
      { ...analysis.questions[1], status: 'NOT_FOUND', reviewStatus: 'BLOCKIERT', citations: [], clarification: 'Intern klären.' },
    ];
    const capabilities: Capabilities = { mode: 'api', uploadsEnabled: true, aiEnabled: false, exportFormats: ['XLSX'] };
    const onExport = vi.fn();

    const review = render(<ReviewExport analysis={analysis} capabilities={capabilities} onOpenQuestion={vi.fn()} onNavigate={vi.fn()} onExport={onExport} exporting={false} />);

    expect(screen.getByRole('heading', { name: 'Review abgeschlossen.' })).toBeInTheDocument();
    expect(screen.getByText(/2 von 2 Antworten sind entschieden/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Bewusste Klärungen' })).toBeInTheDocument();
    const exportButton = screen.getByRole('button', { name: 'XLSX exportieren' });
    expect(exportButton).toBeEnabled();
    await user.click(exportButton);
    expect(onExport).toHaveBeenCalledOnce();

    review.rerender(<ReviewExport analysis={{ ...analysis, questions: [analysis.questions[0], { ...analysis.questions[1], reviewStatus: 'OFFEN' }] }} capabilities={capabilities} onOpenQuestion={vi.fn()} onNavigate={vi.fn()} onExport={onExport} exporting={false} />);
    expect(screen.getByRole('heading', { name: 'Noch Entscheidungen offen.' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'XLSX exportieren' })).toBeDisabled();
  });

  it('zeigt im Offen-Filter ausschließlich noch unentschiedene Fragen', async () => {
    const user = userEvent.setup();
    const analysis = compactAnalysis();
    analysis.questions = [
      { ...analysis.questions[0], reviewStatus: 'BLOCKIERT' },
      { ...analysis.questions[1], reviewStatus: 'OFFEN' },
    ];

    const questions = render(<Questions analysis={analysis} onOpenQuestion={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: /Nur offen/i }));

    expect(screen.queryByText(analysis.questions[0].id)).not.toBeInTheDocument();
    expect(screen.getByText(analysis.questions[1].id)).toBeInTheDocument();

    questions.rerender(<Questions analysis={{ ...analysis, questions: analysis.questions.map((question) => ({ ...question, reviewStatus: 'BLOCKIERT' })) }} onOpenQuestion={vi.fn()} />);
    expect(screen.getByRole('button', { name: /Review abgeschlossen/i })).toBeDisabled();
  });
});
