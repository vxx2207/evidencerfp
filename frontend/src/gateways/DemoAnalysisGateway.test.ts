import { strFromU8, unzipSync } from 'fflate';
import { describe, expect, it } from 'vitest';
import { escapeSpreadsheetFormula, XLSX_MIME_TYPE } from '../export/evidencePack';
import { DemoAnalysisGateway } from './DemoAnalysisGateway';

describe('DemoAnalysisGateway', () => {
  it('liefert den vollständigen synthetischen Analysevertrag', async () => {
    const gateway = new DemoAnalysisGateway();
    const [capabilities, analysis] = await Promise.all([
      gateway.getCapabilities(),
      gateway.getAnalysis(),
    ]);

    expect(capabilities).toMatchObject({
      mode: 'demo',
      uploadsEnabled: false,
      aiEnabled: false,
      exportFormats: ['XLSX'],
    });
    expect(analysis.questions).toHaveLength(24);
    expect(analysis.documents).toHaveLength(5);
    expect(analysis.findings).toHaveLength(3);
    expect(analysis.questions.some((question) => question.conflict)).toBe(true);
    expect(analysis.questions.filter((question) => question.status === 'NOT_FOUND')).toHaveLength(3);
  });

  it('persistiert Review- und Antwortänderungen über Gateway-Instanzen', async () => {
    const gateway = new DemoAnalysisGateway();
    const original = await gateway.getAnalysis();

    await gateway.updateQuestion(original.id, 'IAM-004', {
      answer: 'Fachlich geprüfte Demo-Antwort.',
      reviewStatus: 'FREIGEGEBEN',
      expectedRevision: original.questions.find((question) => question.id === 'IAM-004')?.revision,
    });

    const reloaded = await new DemoAnalysisGateway().getAnalysis();
    expect(reloaded.questions.find((question) => question.id === 'IAM-004')).toMatchObject({
      answer: 'Fachlich geprüfte Demo-Antwort.',
      reviewStatus: 'FREIGEGEBEN',
      updatedAt: 'Gerade eben',
      revision: 2,
    });
  });

  it('weist parallele Änderungen mit einer veralteten Revision zurück', async () => {
    const gateway = new DemoAnalysisGateway();
    const analysis = await gateway.resetAnalysis();
    const question = analysis.questions.find((item) => item.id === 'IAM-004')!;

    await gateway.updateQuestion(analysis.id, question.id, {
      answer: 'Erste Änderung',
      expectedRevision: question.revision,
    });

    await expect(gateway.updateQuestion(analysis.id, question.id, {
      answer: 'Veraltete zweite Änderung',
      expectedRevision: question.revision,
    })).rejects.toThrow(/zwischenzeitlich geändert/);
  });

  it('setzt lokale Demo-Änderungen auf das Fixture zurück', async () => {
    const gateway = new DemoAnalysisGateway();
    const original = await gateway.getAnalysis();
    const originalQuestion = original.questions.find((question) => question.id === 'IAM-001');

    await gateway.updateQuestion(original.id, 'IAM-001', {
      answer: 'Temporäre Änderung',
      reviewStatus: 'BLOCKIERT',
    });

    const reset = await gateway.resetAnalysis();
    expect(reset.questions.find((question) => question.id === 'IAM-001')).toEqual(originalQuestion);
    expect(window.localStorage).toHaveLength(0);

    reset.questions[0].answer = 'Nur den zurückgegebenen Clone ändern';
    const reloaded = await gateway.getAnalysis();
    expect(reloaded.questions.find((question) => question.id === 'IAM-001')).toEqual(originalQuestion);
  });

  it('erstellt ein XLSX Evidence Pack mit vier Blättern und neutralisierten Formeln', async () => {
    const gateway = new DemoAnalysisGateway();
    const analysis = await gateway.getAnalysis();
    analysis.questions[0].answer = '=2+2';
    analysis.questions[1].text = '-4+4';
    analysis.findings[0].recommendation = '+3+3';
    analysis.documents[0].sections[0].content = '@SUM(A1:A2)';

    const blob = await gateway.exportAnalysis(analysis);
    expect(blob.type).toBe(XLSX_MIME_TYPE);

    const archive = unzipSync(new Uint8Array(await blob.arrayBuffer()));
    const workbookXml = strFromU8(archive['xl/workbook.xml']);
    expect(workbookXml).toContain('name="Antworten"');
    expect(workbookXml).toContain('name="IAM Findings"');
    expect(workbookXml).toContain('name="Quellen"');
    expect(workbookXml).toContain('name="Review"');

    const allXml = Object.entries(archive)
      .filter(([path]) => path.endsWith('.xml'))
      .map(([, bytes]) => strFromU8(bytes))
      .join('\n');
    expect(allXml).toContain("'=2+2");
    expect(allXml).toContain("'+3+3");
    expect(allXml).toContain("'-4+4");
    expect(allXml).toContain("'@SUM(A1:A2)");
    expect(allXml).not.toContain('<f>');
  });
});

describe('escapeSpreadsheetFormula', () => {
  it.each([
    ['=SUM(A1:A2)', "'=SUM(A1:A2)"],
    ['+cmd', "'+cmd"],
    ['-1+1', "'-1+1"],
    ['@SUM(A1:A2)', "'@SUM(A1:A2)"],
    ['  =2+2', "'  =2+2"],
    ['Normaler Text', 'Normaler Text'],
  ])('schützt %s als Text', (input, expected) => {
    expect(escapeSpreadsheetFormula(input)).toBe(expected);
  });
});
