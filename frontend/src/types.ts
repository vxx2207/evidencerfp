export type AnswerStatus = 'SUPPORTED' | 'PARTIAL' | 'NOT_FOUND';
export type ReviewStatus = 'OFFEN' | 'FREIGEGEBEN' | 'BLOCKIERT';
export type FindingSeverity = 'KRITISCH' | 'HOCH' | 'MITTEL';

export interface Citation {
  id: string;
  documentId: string;
  chunkId: string;
  section: string;
  page: number;
  quote: string;
  relevance: string;
}

export interface Question {
  id: string;
  category: string;
  text: string;
  status: AnswerStatus;
  reviewStatus: ReviewStatus;
  answer: string;
  rationale: string;
  updatedAt: string;
  citations: Citation[];
  clarification?: string;
  conflict?: {
    title: string;
    sourceA: string;
    sourceB: string;
  };
}

export interface EvidenceSection {
  id: string;
  title: string;
  page: number;
  content: string;
}

export interface EvidenceDocument {
  id: string;
  title: string;
  type: 'PDF' | 'DOCX';
  version: string;
  publishedAt: string;
  owner: string;
  size: string;
  summary: string;
  tags: string[];
  sections: EvidenceSection[];
}

export interface IamFinding {
  id: string;
  rule: string;
  code: string;
  severity: FindingSeverity;
  object: string;
  description: string;
  recommendation: string;
  evidence: string;
  control: string;
}

export interface Analysis {
  id: string;
  name: string;
  customer: string;
  createdAt: string;
  status: 'COMPLETED' | 'PROCESSING';
  progress: number;
  questions: Question[];
  documents: EvidenceDocument[];
  findings: IamFinding[];
}

export interface Capabilities {
  mode: 'demo' | 'api';
  uploadsEnabled: boolean;
  aiEnabled: boolean;
  exportFormats: string[];
  models?: { chat: string; embeddings: string };
}

export interface QuestionUpdate {
  answer?: string;
  reviewStatus?: ReviewStatus;
}

export interface AnalysisInput {
  customerName: string;
  rfp: File;
  evidence: File[];
  iamSnapshot?: File;
}

export interface AnalysisProgress {
  status: 'PROCESSING' | 'COMPLETED';
  percent: number;
  phase: string;
}

export interface AnalysisGateway {
  getCapabilities(): Promise<Capabilities>;
  getAnalysis(id?: string): Promise<Analysis>;
  createAnalysis(input: AnalysisInput, onProgress?: (progress: AnalysisProgress) => void): Promise<Analysis>;
  updateQuestion(analysisId: string, questionId: string, update: QuestionUpdate): Promise<Question>;
  exportAnalysis(analysis: Analysis): Promise<Blob>;
  resetAnalysis?(): Promise<Analysis>;
}

export type AppView = 'dashboard' | 'questions' | 'workbench' | 'findings' | 'evidence' | 'review';
