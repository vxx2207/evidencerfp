import fixture from '../data/analysis.json';
import type { Analysis, AnalysisGateway, AnalysisInput, Capabilities, Question, QuestionUpdate } from '../types';

const STORAGE_KEY = 'evidence-rfp-demo-analysis-v2';

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

export class DemoAnalysisGateway implements AnalysisGateway {
  private analysis = loadAnalysis();

  async getCapabilities(): Promise<Capabilities> {
    return {
      mode: 'demo',
      uploadsEnabled: false,
      aiEnabled: false,
      exportFormats: ['XLSX'],
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
    const { createEvidencePack } = await import('../export/evidencePack');
    return createEvidencePack(analysis);
  }

  async resetAnalysis(): Promise<Analysis> {
    if (typeof window !== 'undefined') window.localStorage.removeItem(STORAGE_KEY);
    this.analysis = freshAnalysis();
    return structuredClone(this.analysis);
  }
}
