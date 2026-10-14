import fixture from '../data/analysis.json';
import type { Analysis, AnalysisGateway, AnalysisInput, Capabilities, Question, QuestionUpdate } from '../types';

const STORAGE_KEY = 'evidence-rfp-demo-analysis-v1';

function freshAnalysis(): Analysis {
  return structuredClone(fixture) as Analysis;
}

function loadAnalysis(): Analysis {
  if (typeof window === 'undefined') return freshAnalysis();
  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (!stored) return freshAnalysis();
  try {
    return JSON.parse(stored) as Analysis;
  } catch {
    return freshAnalysis();
  }
}

function csvCell(value: string | number): string {
  const normalized = String(value).replace(/\r?\n/g, ' ');
  return `"${normalized.replace(/"/g, '""')}"`;
}

export class DemoAnalysisGateway implements AnalysisGateway {
  private analysis = loadAnalysis();

  async getCapabilities(): Promise<Capabilities> {
    return {
      mode: 'demo',
      uploadsEnabled: false,
      aiEnabled: false,
      exportFormats: ['CSV'],
      models: { chat: 'Vorberechnete Demo', embeddings: 'Vorberechnete Demo' },
    };
  }

  async getAnalysis(): Promise<Analysis> {
    return structuredClone(this.analysis);
  }

  async createAnalysis(_input: AnalysisInput): Promise<Analysis> {
    throw new Error('Uploads sind in der öffentlichen Demo deaktiviert.');
  }

  async updateQuestion(_analysisId: string, questionId: string, update: QuestionUpdate): Promise<Question> {
    const question = this.analysis.questions.find((item) => item.id === questionId);
    if (!question) throw new Error(`Frage ${questionId} wurde nicht gefunden.`);
    Object.assign(question, update, { updatedAt: 'Gerade eben' });
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(this.analysis));
    return structuredClone(question);
  }

  async exportAnalysis(analysis: Analysis): Promise<Blob> {
    const headers = ['ID', 'Kategorie', 'Frage', 'Status', 'Review', 'Antwort', 'Quellen'];
    const rows = analysis.questions.map((question) => [
      question.id,
      question.category,
      question.text,
      question.status,
      question.reviewStatus,
      question.answer,
      question.citations.map((citation) => `${citation.section} (S. ${citation.page})`).join(' | '),
    ]);
    const csv = [headers, ...rows].map((row) => row.map(csvCell).join(';')).join('\r\n');
    return new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' });
  }
}
