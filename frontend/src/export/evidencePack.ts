import type { Analysis, EvidenceDocument, Question } from '../types';

export const XLSX_MIME_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

type CellValue = string | number;

interface WorkbookCell {
  value: CellValue;
  fontWeight?: 'bold';
  textColor?: string;
  backgroundColor?: string;
  align?: 'left' | 'center';
  verticalAlign?: 'top';
  wrap?: boolean;
}

interface WorkbookSheet {
  data: WorkbookCell[][];
  sheet: string;
  columns: Array<{ width: number }>;
  stickyRowsCount: number;
}

const HEADER_STYLE = {
  fontWeight: 'bold',
  textColor: '#FFFFFF',
  backgroundColor: '#14324A',
  align: 'left',
  verticalAlign: 'top',
  wrap: true,
} as const;

const DATA_STYLE = {
  align: 'left',
  verticalAlign: 'top',
  wrap: true,
} as const;

export function escapeSpreadsheetFormula(value: string): string {
  const cleaned = value.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, ' ');
  return /^\s*[=+\-@]/.test(cleaned) ? `'${cleaned}` : cleaned;
}

function safeValue(value: CellValue | null | undefined): CellValue {
  return typeof value === 'string' ? escapeSpreadsheetFormula(value) : value ?? '';
}

function header(value: string): WorkbookCell {
  return { value: safeValue(value), ...HEADER_STYLE };
}

function cell(value: CellValue | null | undefined): WorkbookCell {
  return { value: safeValue(value), ...DATA_STYLE };
}

function citationSummary(question: Question): string {
  return question.citations
    .map((citation) => `${citation.section} (S. ${citation.page}): ${citation.quote}`)
    .join('\n');
}

function conflictSummary(question: Question): string {
  if (!question.conflict) return '';
  return `${question.conflict.title}: ${question.conflict.sourceA} <> ${question.conflict.sourceB}`;
}

function answersSheet(analysis: Analysis): WorkbookSheet {
  const data = [
    ['ID', 'Kategorie', 'Frage', 'Antwortstatus', 'Reviewstatus', 'Antwort', 'Begründung', 'Klärungsfrage', 'Quellen', 'Letzte Änderung'].map(header),
    ...analysis.questions.map((question) => [
      cell(question.id),
      cell(question.category),
      cell(question.text),
      cell(question.status),
      cell(question.reviewStatus),
      cell(question.answer),
      cell(question.rationale),
      cell(question.clarification),
      cell(citationSummary(question)),
      cell(question.updatedAt),
    ]),
  ];

  return {
    data,
    sheet: 'Antworten',
    columns: [12, 20, 48, 16, 16, 58, 46, 46, 58, 18].map((width) => ({ width })),
    stickyRowsCount: 1,
  };
}

function findingsSheet(analysis: Analysis): WorkbookSheet {
  const data = [
    ['ID', 'Regelcode', 'Regel', 'Schweregrad', 'Betroffenes Objekt', 'Beschreibung', 'Evidenz', 'Empfehlung', 'Kontrolle'].map(header),
    ...analysis.findings.map((finding) => [
      cell(finding.id),
      cell(finding.code),
      cell(finding.rule),
      cell(finding.severity),
      cell(finding.object),
      cell(finding.description),
      cell(finding.evidence),
      cell(finding.recommendation),
      cell(finding.control),
    ]),
  ];

  return {
    data,
    sheet: 'IAM Findings',
    columns: [14, 15, 30, 14, 30, 52, 46, 52, 24].map((width) => ({ width })),
    stickyRowsCount: 1,
  };
}

function sourceRows(document: EvidenceDocument): WorkbookCell[][] {
  const documentCells = [
    document.id,
    document.title,
    document.type,
    document.version,
    document.publishedAt,
    document.owner,
  ];

  if (document.sections.length === 0) {
    return [[...documentCells.map(cell), cell(''), cell(''), cell(''), cell('')]];
  }

  return document.sections.map((section) => [
    ...documentCells.map(cell),
    cell(section.id),
    cell(section.title),
    cell(section.page),
    cell(section.content),
  ]);
}

function sourcesSheet(analysis: Analysis): WorkbookSheet {
  const data = [
    ['Dokument-ID', 'Dokument', 'Typ', 'Version', 'Veröffentlicht', 'Owner', 'Chunk-ID', 'Abschnitt', 'Seite', 'Vollständige Passage'].map(header),
    ...analysis.documents.flatMap(sourceRows),
  ];

  return {
    data,
    sheet: 'Quellen',
    columns: [18, 38, 10, 12, 16, 24, 22, 32, 9, 76].map((width) => ({ width })),
    stickyRowsCount: 1,
  };
}

function reviewSheet(analysis: Analysis): WorkbookSheet {
  const data = [
    ['ID', 'Frage', 'Antwortstatus', 'Reviewentscheidung', 'Konflikt', 'Klärungsfrage', 'Letzte Änderung'].map(header),
    ...analysis.questions.map((question) => [
      cell(question.id),
      cell(question.text),
      cell(question.status),
      cell(question.reviewStatus),
      cell(conflictSummary(question)),
      cell(question.clarification),
      cell(question.updatedAt),
    ]),
  ];

  return {
    data,
    sheet: 'Review',
    columns: [12, 54, 17, 19, 54, 52, 18].map((width) => ({ width })),
    stickyRowsCount: 1,
  };
}

export function buildEvidencePackSheets(analysis: Analysis): WorkbookSheet[] {
  return [answersSheet(analysis), findingsSheet(analysis), sourcesSheet(analysis), reviewSheet(analysis)];
}

export async function createEvidencePack(analysis: Analysis): Promise<Blob> {
  const { default: writeXlsxFile } = await import('write-excel-file/browser');
  const generated = await writeXlsxFile(buildEvidencePackSheets(analysis)).toBlob();
  if (generated.type === XLSX_MIME_TYPE) return generated;
  return new Blob([await generated.arrayBuffer()], { type: XLSX_MIME_TYPE });
}
