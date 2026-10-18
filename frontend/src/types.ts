export type AnswerStatus = 'SUPPORTED' | 'PARTIAL' | 'NOT_FOUND';
export type ReviewStatus = 'OFFEN' | 'FREIGEGEBEN' | 'BLOCKIERT';
export type FindingSeverity = 'KRITISCH' | 'HOCH' | 'MITTEL';
export type AnalysisStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
export type AnalysisPhase = 'QUEUED' | 'PARSING' | 'INDEXING' | 'ANSWERING' | 'IAM_CHECKS' | 'PERSISTING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface RetrievalProvenance {
  methods: Array<'DENSE' | 'FULL_TEXT' | 'LEXICAL_FALLBACK'>;
  denseRank?: number;
  fullTextRank?: number;
  fusedRank?: number;
  fusedScore?: number;
  chunkHash?: string;
  retrievalVersion?: string;
  embeddingModel?: string;
}

export interface Citation {
  id: string;
  documentId: string;
  chunkId: string;
  section: string;
  page: number;
  quote: string;
  relevance: string;
  retrieval?: RetrievalProvenance;
}

export interface AnswerClaim {
  text: string;
  citationIds: string[];
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
  revision?: number;
  citations: Citation[];
  claims?: AnswerClaim[];
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
  status: AnalysisStatus;
  progress: number;
  phase?: AnalysisPhase;
  attempt?: number;
  reviewRevision?: number;
  failure?: { code: string; message: string; retryable: boolean };
  technicalProof?: {
    retrievalVersion: string;
    promptVersion: string;
    embeddingModel: string;
    chatModel: string;
    citationValidation: string;
    dataBoundary: string;
  };
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
  retrievalVersion?: string;
  dataBoundary?: string;
  limits?: {
    maxFileBytes: number;
    maxTotalBytes: number;
    maxEvidenceFiles: number;
    maxPdfPages: number;
  };
}

export interface QuestionUpdate {
  answer?: string;
  reviewStatus?: ReviewStatus;
  expectedRevision?: number;
}

export interface AnalysisInput {
  customerName: string;
  rfp: File;
  evidence: File[];
  iamSnapshot?: File;
}

export interface AnalysisProgress {
  status: AnalysisStatus;
  percent: number;
  phase: string;
  attempt?: number;
  failureCode?: string;
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
