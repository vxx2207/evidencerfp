import type {
  Analysis,
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

type JsonRecord = Record<string, unknown>;

const DEMO_ID = '11111111-1111-1111-1111-111111111111';

function text(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

function array(value: unknown): JsonRecord[] {
  return Array.isArray(value) ? value.filter((item): item is JsonRecord => Boolean(item) && typeof item === 'object') : [];
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
    return {
      id: text(citation.id, chunkId),
      documentId: text(citation.documentId ?? citation.documentName),
      chunkId,
      section: locator,
      page: Number(citation.page ?? pageFromLocator(locator)),
      quote: text(citation.quote),
      relevance: text(citation.relevance, 'Serverseitig validierte Evidenz'),
    };
  });
  return {
    id: text(raw.id),
    category: text(raw.category, 'Allgemein'),
    text: text(raw.question ?? raw.text),
    status: text(raw.answerStatus ?? raw.status, 'NOT_FOUND') as AnswerStatus,
    reviewStatus: reviewStatus(raw.reviewStatus),
    answer: text(raw.answer),
    rationale: text(raw.rationale, 'Antwort wurde anhand der abgerufenen Evidenz bewertet.'),
    updatedAt: text(raw.updatedAt, 'Gerade eben'),
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

  private async json(path: string, init?: RequestInit): Promise<JsonRecord> {
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
    return response.json() as Promise<JsonRecord>;
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
        status: state === 'COMPLETED' ? 'COMPLETED' : 'PROCESSING',
        percent: Number(status.progressPercent ?? (state === 'COMPLETED' ? 100 : 50)),
        phase: text(status.phase, state === 'COMPLETED' ? 'Analyse abgeschlossen' : 'Evidenz wird verarbeitet'),
      });
      if (state === 'COMPLETED') return;
      if (state === 'FAILED') {
        throw new Error('Die Analyse ist fehlgeschlagen. Bitte Dateien und Backend-Protokoll prüfen.');
      }
      await new Promise((resolve) => window.setTimeout(resolve, this.pollIntervalMs));
      status = await this.json(`/api/v1/analyses/${id}`);
    }
    throw new Error('Die Analyse dauert länger als erwartet. Sie läuft möglicherweise im Hintergrund weiter.');
  }

  async getCapabilities(): Promise<Capabilities> {
    const data = await this.json('/api/v1/capabilities');
    return {
      mode: 'api',
      uploadsEnabled: true,
      aiEnabled: Boolean(data.aiAvailable),
      exportFormats: ['XLSX'],
      models: { chat: text(data.aiProvider, 'Ollama'), embeddings: 'bge-m3' },
    };
  }

  async getAnalysis(id = DEMO_ID): Promise<Analysis> {
    const [status, result] = await Promise.all([
      this.json(`/api/v1/analyses/${id}`),
      this.json(`/api/v1/analyses/${id}/result`),
    ]);
    const questions = array(result.questions).map(mapQuestion);
    return {
      id: text(result.id, id),
      name: 'Identity Security Assessment',
      customer: text(result.customerName, 'Unbenannte Analyse'),
      createdAt: text(status.createdAt, new Date().toISOString()),
      status: text(result.status ?? status.status) === 'COMPLETED' ? 'COMPLETED' : 'PROCESSING',
      progress: Number(status.progressPercent ?? 0),
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
    const status = await this.json(`/api/v1/analyses?${query.toString()}`, { method: 'POST', body });
    const id = text(status.id);
    if (!id) throw new Error('Die API hat keine Analyse-ID zurückgegeben.');
    await this.waitForCompletion(id, status, onProgress);
    return this.getAnalysis(id);
  }

  async updateQuestion(analysisId: string, questionId: string, update: QuestionUpdate): Promise<Question> {
    const payload = {
      ...update,
      reviewStatus: update.reviewStatus ? apiReviewStatus(update.reviewStatus) : undefined,
    };
    const result = await this.json(`/api/v1/analyses/${analysisId}/questions/${questionId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return mapQuestion(result);
  }

  async exportAnalysis(analysis: Analysis): Promise<Blob> {
    const response = await fetch(`${this.baseUrl}/api/v1/analyses/${analysis.id}/export.xlsx`);
    if (!response.ok) throw new Error(`Export fehlgeschlagen (${response.status}).`);
    return response.blob();
  }
}
