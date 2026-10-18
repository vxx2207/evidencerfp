import fixture from '../data/analysis.json';
import type { Analysis, AnalysisGateway, AnalysisInput, Capabilities, Question, QuestionUpdate } from '../types';

const STORAGE_KEY = 'evidence-rfp-demo-analysis-v2';

function freshAnalysis(): Analysis {
  const analysis = structuredClone(fixture) as Analysis;
  analysis.phase = 'COMPLETED';
  analysis.attempt = 1;
  analysis.reviewRevision = 23;
  analysis.technicalProof = {
    retrievalVersion: 'hybrid-pgvector-fts-rrf-v1',
    promptVersion: 'claims-v1',
    embeddingModel: 'bge-m3',
    chatModel: 'llama3.1:8b',
    citationValidation: 'Claim- und Zitatprüfung aktiv',
    dataBoundary: 'Vorberechnete, vollständig synthetische Demo',
  };
  analysis.questions.forEach((question) => { question.revision ??= 1; });
  return analysis;
}

function loadAnalysis(): Analysis {
  if (typeof window === 'undefined') return freshAnalysis();
  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (!stored) return freshAnalysis();
  try {
    const baseline = freshAnalysis();
    const parsed = JSON.parse(stored) as Analysis;
    return {
      ...baseline,
      ...parsed,
      technicalProof: parsed.technicalProof ?? baseline.technicalProof,
      questions: parsed.questions.map((question) => ({ ...question, revision: question.revision ?? 1 })),
    };
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
      retrievalVersion: 'hybrid-pgvector-fts-rrf-v1',
      dataBoundary: 'Vorberechnete, vollständig synthetische Demo',
      limits: {
        maxFileBytes: 10_485_760,
        maxTotalBytes: 52_428_800,
        maxEvidenceFiles: 20,
        maxPdfPages: 200,
      },
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
    const { expectedRevision, ...changes } = update;
    const currentRevision = question.revision ?? 0;
    if (expectedRevision !== undefined && expectedRevision !== currentRevision) {
      throw new Error('Die Antwort wurde zwischenzeitlich geändert. Bitte lade den aktuellen Stand.');
    }
    Object.assign(question, changes, { updatedAt: 'Gerade eben', revision: currentRevision + 1 });
    this.analysis.reviewRevision = (this.analysis.reviewRevision ?? 0) + 1;
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
