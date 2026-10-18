import type {
  Analysis,
  AnalysisStatus,
  AnalysisInput,
  AnalysisProgress,
  AnalysisGateway,
  AnswerStatus,
  Capabilities,
  EvidenceDocument,
  IamFinding,
  Question,
  QuestionUpdate,
  ReviewStatus,
} from '../types';
import type {
  AnalysisResult as ApiAnalysisResult,
  AnalysisStatus as ApiAnalysisStatus,
  Capabilities as ApiCapabilities,
  Question as ApiQuestion,
} from '../generated/api';

type JsonRecord = Record<string, unknown>;

const DEMO_ID = '11111111-1111-1111-1111-111111111111';

function text(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

function array(value: unknown): JsonRecord[] {
  return Array.isArray(value) ? value.filter((item): item is JsonRecord => Boolean(item) && typeof item === 'object') : [];
}

function record(value: unknown): JsonRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as JsonRecord : {};
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.map(String) : [];
}

async function sha256(value: ArrayBuffer | string): Promise<string> {
  const bytes = typeof value === 'string' ? new TextEncoder().encode(value) : value;
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
}

async function idempotencyKey(input: AnalysisInput): Promise<string> {
  const files = [input.rfp, ...input.evidence, ...(input.iamSnapshot ? [input.iamSnapshot] : [])];
  const fileHashes = await Promise.all(files.map(async (file) => `${file.name}:${file.size}:${await sha256(await file.arrayBuffer())}`));
  return `evidencerfp-${(await sha256([input.customerName, ...fileHashes].join('|'))).slice(0, 48)}`;
}

function reviewStatus(value: unknown): ReviewStatus {
  switch (text(value).toUpperCase()) {
    case 'APPROVED':
    case 'FREIGEGEBEN':
      return 'FREIGEGEBEN';
    case 'CHANGES_REQUIRED':
    case 'BLOCKIERT':
      return 'BLOCKIERT';
    default:
      return 'OFFEN';
  }
}

function apiReviewStatus(value: ReviewStatus): 'DRAFT' | 'APPROVED' | 'CHANGES_REQUIRED' {
  if (value === 'FREIGEGEBEN') return 'APPROVED';
  if (value === 'BLOCKIERT') return 'CHANGES_REQUIRED';
  return 'DRAFT';
}

function pageFromLocator(locator: string): number {
  const match = locator.match(/\d+/);
  return match ? Number(match[0]) : 1;
}

function mapQuestion(raw: JsonRecord): Question {
  const findings = array(raw.findings);
  const conflictFinding = findings.find((finding) => text(finding.type) === 'CONFLICTING_EVIDENCE');
  const citations = array(raw.citations).map((citation, index) => {
    const locator = text(citation.locator ?? citation.section, 'Evidenzpassage');
    const chunkId = text(citation.chunkId, `chunk-${index}`);
    const retrieval = record(citation.retrieval ?? citation.retrievalProvenance);
    const methods = stringArray(retrieval.methods ?? citation.retrievalMethods)
      .map((method) => method.toUpperCase())
      .map((method) => method === 'LEXICAL' ? 'FULL_TEXT' : method)
      .filter((method): method is 'DENSE' | 'FULL_TEXT' | 'LEXICAL_FALLBACK' =>
        method === 'DENSE' || method === 'FULL_TEXT' || method === 'LEXICAL_FALLBACK');
    return {
      id: text(citation.id, chunkId),
      documentId: text(citation.documentId ?? citation.documentName),
      chunkId,
      section: locator,
      page: Number(citation.page ?? pageFromLocator(locator)),
      quote: text(citation.quote),
      relevance: text(citation.relevance, 'Serverseitig validierte Evidenz'),
      retrieval: methods.length > 0 ? {
        methods,
        denseRank: Number(retrieval.denseRank ?? citation.denseRank) || undefined,
        fullTextRank: Number(retrieval.fullTextRank ?? retrieval.lexicalRank ?? citation.fullTextRank ?? citation.lexicalRank) || undefined,
        fusedRank: Number(retrieval.fusedRank ?? citation.fusedRank) || undefined,
        fusedScore: Number(retrieval.fusedScore ?? citation.fusedScore) || undefined,
        chunkHash: text(retrieval.chunkHash ?? citation.chunkHash) || undefined,
        retrievalVersion: text(retrieval.retrievalVersion ?? citation.retrievalVersion) || undefined,
        embeddingModel: text(retrieval.embeddingModel ?? citation.embeddingModel) || undefined,
      } : undefined,
    };
  });
  const claims = array(raw.claims).map((claim) => ({
    text: text(claim.text),
    citationIds: stringArray(claim.citationIds ?? claim.citations),
  })).filter((claim) => claim.text.length > 0);
  return {
    id: text(raw.id),
    category: text(raw.category, 'Allgemein'),
    text: text(raw.question ?? raw.text),
    status: text(raw.answerStatus ?? raw.status, 'NOT_FOUND') as AnswerStatus,
    reviewStatus: reviewStatus(raw.reviewStatus),
    answer: text(raw.answer),
    rationale: text(raw.rationale, 'Antwort wurde anhand der abgerufenen Evidenz bewertet.'),
    updatedAt: text(raw.updatedAt, 'Gerade eben'),
    revision: Number(raw.revision ?? 0),
    clarification: text(raw.internalClarification ?? raw.clarification) || undefined,
    conflict: raw.conflict && typeof raw.conflict === 'object'
      ? raw.conflict as Question['conflict']
      : conflictFinding
        ? {
            title: text(conflictFinding.title, 'Widersprüchliche Evidenz'),
            sourceA: citations[0]?.quote ?? 'Quelle A',
            sourceB: citations[1]?.quote ?? 'Quelle B',
          }
        : undefined,
    citations,
    claims: claims.length > 0 ? claims : undefined,
  };
}

function mapDocument(raw: JsonRecord, questions: Question[]): EvidenceDocument {
  const fileName = text(raw.name ?? raw.title ?? raw.fileName, 'Evidenzdokument');
  const mediaType = text(raw.mediaType ?? raw.type).toLowerCase();
  const persistedPassages = array(raw.passages).map((passage, index) => {
    const locator = text(passage.locator ?? passage.title, 'Evidenzpassage');
    return {
      id: text(passage.chunkId ?? passage.id, `passage-${index}`),
      title: locator,
      page: Number(passage.page ?? pageFromLocator(locator)),
      content: text(passage.content ?? passage.text),
    };
  });
  const citedPassages = questions.flatMap((question) => question.citations)
    .filter((citation) => citation.documentId === fileName)
    .filter((citation, index, all) => all.findIndex((item) => item.chunkId === citation.chunkId) === index)
    .map((citation) => ({
      id: citation.chunkId,
      title: citation.section,
      page: citation.page,
      content: citation.quote,
    }));
  const sections = persistedPassages.length > 0 ? persistedPassages : citedPassages;
  return {
    id: text(raw.id, fileName),
    title: fileName,
    type: mediaType.includes('wordprocessingml') || fileName.toLowerCase().endsWith('.docx') ? 'DOCX' : 'PDF',
    version: text(raw.version, raw.stale === true ? 'Veraltet' : 'Aktuell'),
    publishedAt: text(raw.documentDate ?? raw.publishedAt ?? raw.uploadedAt, new Date().toISOString()),
    owner: text(raw.owner, 'CertaAccess'),
    size: text(raw.size, '—'),
    summary: text(raw.summary, `${Number(raw.chunkCount ?? sections.length)} indexierte Evidenzpassagen.`),
    tags: Array.isArray(raw.tags) ? raw.tags.map(String) : raw.stale === true ? ['Veraltet'] : ['Verifiziert'],
    sections: sections.length > 0 ? sections : array(raw.sections ?? raw.chunks).map((section, index) => ({
      id: text(section.id, `section-${index}`),
      title: text(section.title ?? section.section, 'Evidenzpassage'),
      page: Number(section.page ?? 1),
      content: text(section.content ?? section.text),
    })),
  };
}

function mapFinding(raw: JsonRecord): IamFinding {
  const severity = text(raw.severity).toUpperCase();
  return {
    id: text(raw.id),
    rule: text(raw.rule ?? raw.title),
    code: text(raw.code ?? raw.ruleCode ?? raw.ruleId),
    severity: severity === 'CRITICAL' ? 'KRITISCH' : severity === 'HIGH' ? 'HOCH' : 'MITTEL',
    object: text(raw.object ?? raw.affectedObject),
    description: text(raw.description),
    recommendation: text(raw.recommendation),
    evidence: text(raw.evidence, text(raw.affectedObject)),
    control: text(raw.control, text(raw.type, 'IAM_CONTROL_FAILED')),
  };
}

export class ApiAnalysisGateway implements AnalysisGateway {
  constructor(
    private readonly baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
    private readonly pollIntervalMs = 750,
    private readonly maxPolls = 400,
  ) {}

  private async json<T = JsonRecord>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, init);
    if (!response.ok) {
      let detail = '';
      try {
        const problem = await response.json() as JsonRecord;
        detail = text(problem.detail ?? problem.title);
      } catch {
        // Nicht jede Infrastrukturantwort enthält RFC-9457-JSON.
      }
      throw new Error(detail || `API-Fehler ${response.status}: ${response.statusText}`);
    }
    return response.json() as Promise<T>;
  }

  private async waitForCompletion(
    id: string,
    initialStatus: JsonRecord,
    onProgress?: (progress: AnalysisProgress) => void,
  ): Promise<void> {
    let status = initialStatus;
    for (let attempt = 0; attempt < this.maxPolls; attempt += 1) {
      const state = text(status.status).toUpperCase();
      onProgress?.({
        status: (state || 'PROCESSING') as AnalysisStatus,
        percent: Number(status.progressPercent ?? (state === 'COMPLETED' ? 100 : 50)),
        phase: text(status.phase, state === 'COMPLETED' ? 'Analyse abgeschlossen' : 'Evidenz wird verarbeitet'),
        attempt: Number(status.attempt ?? 0),
        failureCode: text(record(status.failure).code ?? status.failureCode) || undefined,
      });
      if (state === 'COMPLETED') return;
      if (state === 'FAILED' || state === 'CANCELLED') {
        const failure = record(status.failure);
        const failureCode = text(failure.code ?? status.failureCode);
        throw new Error(text(failure.message ?? status.failureMessage,
          state === 'CANCELLED' ? 'Die Analyse wurde abgebrochen.'
            : `Die Analyse ist fehlgeschlagen${failureCode ? ` (${failureCode})` : ''}.`));
      }
      await new Promise((resolve) => window.setTimeout(resolve, this.pollIntervalMs));
      status = record(await this.json<ApiAnalysisStatus>(`/api/v1/analyses/${id}`));
    }
    throw new Error('Die Analyse dauert länger als erwartet. Sie läuft möglicherweise im Hintergrund weiter.');
  }

  async getCapabilities(): Promise<Capabilities> {
    const data = record(await this.json<ApiCapabilities>('/api/v1/capabilities'));
    const limits = record(data.limits);
    const models = record(data.models);
    return {
      mode: 'api',
      uploadsEnabled: true,
      aiEnabled: Boolean(data.aiAvailable),
      exportFormats: ['XLSX'],
      models: {
        chat: text(models.chat ?? data.chatModel ?? data.aiProvider, 'Ollama'),
        embeddings: text(models.embeddings ?? data.embeddingModel, 'bge-m3'),
      },
      retrievalVersion: text(data.retrievalVersion, 'hybrid-pgvector-fts-rrf-v1'),
      dataBoundary: text(data.dataBoundary, 'Lokale Verarbeitung auf 127.0.0.1'),
      limits: {
        maxFileBytes: Number(limits.maxFileBytes ?? data.maxFileSizeBytes ?? 10_485_760),
        maxTotalBytes: Number(limits.maxTotalBytes ?? 52_428_800),
        maxEvidenceFiles: Number(limits.maxEvidenceFiles ?? 20),
        maxPdfPages: Number(limits.maxPdfPages ?? 200),
      },
    };
  }

  async getAnalysis(id = DEMO_ID): Promise<Analysis> {
    const [statusValue, resultValue] = await Promise.all([
      this.json<ApiAnalysisStatus>(`/api/v1/analyses/${id}`),
      this.json<ApiAnalysisResult>(`/api/v1/analyses/${id}/result`),
    ]);
    const status = record(statusValue);
    const result = record(resultValue);
    const questions = array(result.questions).map(mapQuestion);
    const run = record(result.runMetadata ?? result.technicalProof);
    const failure = record(status.failure);
    return {
      id: text(result.id, id),
      name: 'Identity Security Assessment',
      customer: text(result.customerName, 'Unbenannte Analyse'),
      createdAt: text(status.createdAt, new Date().toISOString()),
      status: text(result.status ?? status.status, 'PROCESSING') as AnalysisStatus,
      progress: Number(status.progressPercent ?? 0),
      phase: text(status.phaseCode ?? status.phase, 'COMPLETED') as Analysis['phase'],
      attempt: Number(status.attempt ?? 0),
      reviewRevision: Number(result.reviewRevision ?? 0),
      failure: Object.keys(failure).length > 0 ? {
        code: text(failure.code),
        message: text(failure.message),
        retryable: Boolean(failure.retryable),
      } : undefined,
      technicalProof: {
        retrievalVersion: text(run.retrievalVersion, 'hybrid-pgvector-fts-rrf-v1'),
        promptVersion: text(run.promptVersion, 'claims-v1'),
        embeddingModel: text(run.embeddingModel, 'bge-m3'),
        chatModel: text(run.chatModel, 'llama3.1:8b'),
        citationValidation: text(run.citationValidation, 'Claim- und Zitatprüfung aktiv'),
        dataBoundary: text(run.dataBoundary, 'Lokale Verarbeitung auf 127.0.0.1'),
      },
      questions,
      documents: array(result.evidenceDocuments).map((document) => mapDocument(document, questions)),
      findings: array(result.findings)
        .filter((finding) => text(finding.type) === 'IAM_CONTROL_FAILED')
        .map(mapFinding),
    };
  }

  async createAnalysis(input: AnalysisInput, onProgress?: (progress: AnalysisProgress) => void): Promise<Analysis> {
    const body = new FormData();
    body.append('rfp', input.rfp);
    input.evidence.forEach((file) => body.append('evidence', file));
    if (input.iamSnapshot) body.append('iamSnapshot', input.iamSnapshot);
    const query = new URLSearchParams({ customerName: input.customerName });
    const status = await this.json(`/api/v1/analyses?${query.toString()}`, {
      method: 'POST',
      headers: { 'Idempotency-Key': await idempotencyKey(input) },
      body,
    });
    const id = text(status.id);
    if (!id) throw new Error('Die API hat keine Analyse-ID zurückgegeben.');
    await this.waitForCompletion(id, status, onProgress);
    return this.getAnalysis(id);
  }

  async updateQuestion(analysisId: string, questionId: string, update: QuestionUpdate): Promise<Question> {
    const { expectedRevision, ...changes } = update;
    const payload = {
      ...changes,
      reviewStatus: changes.reviewStatus ? apiReviewStatus(changes.reviewStatus) : undefined,
    };
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (expectedRevision !== undefined) headers['If-Match'] = `"${expectedRevision}"`;
    const result = record(await this.json<ApiQuestion>(`/api/v1/analyses/${analysisId}/questions/${questionId}`, {
      method: 'PATCH',
      headers,
      body: JSON.stringify(payload),
    }));
    return mapQuestion(result);
  }

  async exportAnalysis(analysis: Analysis): Promise<Blob> {
    const response = await fetch(`${this.baseUrl}/api/v1/analyses/${analysis.id}/export.xlsx`);
    if (!response.ok) throw new Error(`Export fehlgeschlagen (${response.status}).`);
    return response.blob();
  }
}
