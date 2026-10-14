import { AlertTriangle, Check, CircleDashed, ShieldCheck } from 'lucide-react';
import type { AnswerStatus, ReviewStatus } from '../types';

const answerLabels: Record<AnswerStatus, string> = {
  SUPPORTED: 'Belegt',
  PARTIAL: 'Teilbelegt',
  NOT_FOUND: 'Nicht gefunden',
};

const reviewLabels: Record<ReviewStatus, string> = {
  OFFEN: 'Offen',
  FREIGEGEBEN: 'Freigegeben',
  BLOCKIERT: 'Blockiert',
};

export function AnswerBadge({ status, compact = false }: { status: AnswerStatus; compact?: boolean }) {
  const Icon = status === 'SUPPORTED' ? ShieldCheck : status === 'PARTIAL' ? AlertTriangle : CircleDashed;
  return (
    <span className={`status-badge status-${status.toLowerCase()}`} title={answerLabels[status]}>
      <Icon size={compact ? 13 : 14} aria-hidden="true" />
      {!compact && answerLabels[status]}
    </span>
  );
}

export function ReviewBadge({ status }: { status: ReviewStatus }) {
  return (
    <span className={`review-badge review-${status.toLowerCase()}`}>
      {status === 'FREIGEGEBEN' && <Check size={12} aria-hidden="true" />}
      {reviewLabels[status]}
    </span>
  );
}

export function answerLabel(status: AnswerStatus) {
  return answerLabels[status];
}
