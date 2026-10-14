import { describe, expect, it } from 'vitest';
import { DemoAnalysisGateway } from './DemoAnalysisGateway';

describe('DemoAnalysisGateway', () => {
  it('liefert den vollständigen synthetischen Analysevertrag', async () => {
    const gateway = new DemoAnalysisGateway();
    const [capabilities, analysis] = await Promise.all([
      gateway.getCapabilities(),
      gateway.getAnalysis(),
    ]);

    expect(capabilities).toMatchObject({ mode: 'demo', uploadsEnabled: false, aiEnabled: false });
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
    });

    const reloaded = await new DemoAnalysisGateway().getAnalysis();
    expect(reloaded.questions.find((question) => question.id === 'IAM-004')).toMatchObject({
      answer: 'Fachlich geprüfte Demo-Antwort.',
      reviewStatus: 'FREIGEGEBEN',
      updatedAt: 'Gerade eben',
    });
  });
});
