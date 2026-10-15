import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiAnalysisGateway } from './ApiAnalysisGateway';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('ApiAnalysisGateway', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('pollt einen angenommenen Job bis zum Ergebnis', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ id: 'analysis-1', status: 'PROCESSING' }, 202))
      .mockResolvedValueOnce(jsonResponse({ id: 'analysis-1', status: 'PROCESSING' }))
      .mockResolvedValueOnce(jsonResponse({ id: 'analysis-1', status: 'COMPLETED' }))
      .mockResolvedValueOnce(jsonResponse({ id: 'analysis-1', status: 'COMPLETED', createdAt: '2026-10-15T00:00:00Z' }))
      .mockResolvedValueOnce(jsonResponse({
        id: 'analysis-1', customerName: 'API-Kunde', status: 'COMPLETED',
        questions: [], findings: [], evidenceDocuments: [],
      }));
    vi.stubGlobal('fetch', fetchMock);
    const gateway = new ApiAnalysisGateway('http://localhost:8080', 0, 5);
    const onProgress = vi.fn();

    const result = await gateway.createAnalysis({
      customerName: 'API-Kunde',
      rfp: new File(['rfp'], 'rfp.xlsx'),
      evidence: [new File(['pdf'], 'beleg.pdf')],
    }, onProgress);

    expect(result.id).toBe('analysis-1');
    expect(result.customer).toBe('API-Kunde');
    expect(fetchMock).toHaveBeenCalledTimes(5);
    expect(onProgress).toHaveBeenLastCalledWith(expect.objectContaining({
      status: 'COMPLETED', percent: 100,
    }));
  });

  it('zeigt den sicheren Problem-Detail-Text der API', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      title: 'Ungültige Eingabe', detail: 'Die Datei besitzt keine gültige XLSX-Signatur.',
    }, 400)));
    const gateway = new ApiAnalysisGateway('http://localhost:8080', 0, 1);

    await expect(gateway.getCapabilities())
      .rejects.toThrow('Die Datei besitzt keine gültige XLSX-Signatur.');
  });
});
