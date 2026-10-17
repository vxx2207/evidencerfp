import type { Question, ReviewStatus } from './types';

export function isReviewOpen(status: ReviewStatus): boolean {
  return status === 'OFFEN';
}

export function isReviewDecided(status: ReviewStatus): boolean {
  return status === 'FREIGEGEBEN' || status === 'BLOCKIERT';
}

export function reviewDecisionProgress(questions: readonly Question[]): number {
  if (questions.length === 0) return 0;
  const decided = questions.filter((question) => isReviewDecided(question.reviewStatus)).length;
  return Math.round((decided / questions.length) * 100);
}
