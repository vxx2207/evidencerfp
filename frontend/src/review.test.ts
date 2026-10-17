import { describe, expect, it } from 'vitest';
import fixture from './data/analysis.json';
import type { Analysis } from './types';
import { isReviewDecided, isReviewOpen, reviewDecisionProgress } from './review';

describe('Review-Semantik', () => {
  it('wertet freigegebene und blockierte Antworten als entschieden', () => {
    expect(isReviewOpen('OFFEN')).toBe(true);
    expect(isReviewDecided('OFFEN')).toBe(false);
    expect(isReviewDecided('FREIGEGEBEN')).toBe(true);
    expect(isReviewDecided('BLOCKIERT')).toBe(true);
  });

  it('berechnet Fortschritt aus allen bewussten Entscheidungen', () => {
    const analysis = structuredClone(fixture) as Analysis;
    const questions = [
      { ...analysis.questions[0], reviewStatus: 'FREIGEGEBEN' as const },
      { ...analysis.questions[1], reviewStatus: 'BLOCKIERT' as const },
      { ...analysis.questions[2], reviewStatus: 'OFFEN' as const },
      { ...analysis.questions[3], reviewStatus: 'OFFEN' as const },
    ];

    expect(reviewDecisionProgress(questions)).toBe(50);
  });
});
