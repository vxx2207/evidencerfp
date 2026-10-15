import { describe, expect, it } from 'vitest';
import { evidencePackFilename, formatAnalysisDateTime } from './format';

describe('Frontend-Formatierung', () => {
  it('erzeugt einen sicheren kundenspezifischen Exportnamen', () => {
    expect(evidencePackFilename('Münchner Bank & Partner', 'xlsx')).toBe('Munchner_Bank_Partner_Evidence_Pack.xlsx');
    expect(evidencePackFilename('---', 'csv')).toBe('Analyse_Evidence_Pack.csv');
  });

  it('fängt ungültige Zeitstempel verständlich ab', () => {
    expect(formatAnalysisDateTime('kein-datum')).toBe('Zeitpunkt unbekannt');
  });
});
