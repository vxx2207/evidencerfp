export function evidencePackFilename(customer: string, extension: 'xlsx' | 'csv'): string {
  const customerStem = customer
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');

  return `${customerStem || 'Analyse'}_Evidence_Pack.${extension}`;
}

export function formatAnalysisDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Zeitpunkt unbekannt';

  return new Intl.DateTimeFormat('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}
