import { render, screen } from '@testing-library/react';
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
    expect(screen.getByText('1 kritisch · 0 hoch · 0 mittel')).toBeInTheDocument();
    expect(screen.getByText('2 Passagen')).toBeInTheDocument();
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
});
