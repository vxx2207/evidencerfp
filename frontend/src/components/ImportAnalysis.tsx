import { FileJson2, FileSpreadsheet, FileText, LoaderCircle, LockKeyhole, Upload, X } from 'lucide-react';
import { useState } from 'react';
import type { AnalysisInput, AnalysisProgress } from '../types';

export function ImportAnalysis({
  open,
  onClose,
  onSubmit,
}: {
  open: boolean;
  onClose: () => void;
  onSubmit: (input: AnalysisInput, onProgress: (progress: AnalysisProgress) => void) => Promise<void>;
}) {
  const [customerName, setCustomerName] = useState('Nordstern Bank (synthetisch)');
  const [rfp, setRfp] = useState<File | null>(null);
  const [evidence, setEvidence] = useState<File[]>([]);
  const [iamSnapshot, setIamSnapshot] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState<AnalysisProgress | null>(null);

  if (!open) return null;

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!rfp || evidence.length === 0) {
      setError('Bitte einen XLSX-Fragebogen und mindestens ein Evidenzdokument auswählen.');
      return;
    }
    setSubmitting(true);
    setError(null);
    setProgress({ status: 'PROCESSING', percent: 5, phase: 'Upload wird validiert' });
    try {
      await onSubmit(
        { customerName: customerName.trim() || 'Lokale Analyse', rfp, evidence, iamSnapshot: iamSnapshot ?? undefined },
        setProgress,
      );
      setRfp(null);
      setEvidence([]);
      setIamSnapshot(null);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Die Analyse konnte nicht gestartet werden.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-shell import-shell" role="dialog" aria-modal="true" aria-labelledby="import-title">
      <button className="modal-backdrop" onClick={onClose} aria-label="Import schließen" />
      <form className="import-dialog" onSubmit={(event) => void submit(event)}>
        <div className="import-header">
          <div><span className="eyebrow">Lokaler Analyse-Lauf</span><h2 id="import-title">Neue Evidence-Analyse</h2><p>Die Dateien werden ausschließlich an dein lokales Spring-Boot-Backend gesendet.</p></div>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Schließen"><X size={19} /></button>
        </div>

        <label className="field-label">Kunde / Opportunity<input value={customerName} maxLength={200} onChange={(event) => setCustomerName(event.target.value)} /></label>

        <div className="import-file-grid">
          <label className={`file-drop ${rfp ? 'selected' : ''}`}>
            <FileSpreadsheet size={22} /><strong>RFP-Fragebogen</strong><span>{rfp?.name ?? '.xlsx · ID, Question, optional Category'}</span>
            <input type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={(event) => setRfp(event.target.files?.[0] ?? null)} />
          </label>
          <label className={`file-drop ${evidence.length ? 'selected' : ''}`}>
            <FileText size={22} /><strong>Produkt-Evidenz</strong><span>{evidence.length ? `${evidence.length} Datei(en) gewählt` : '.pdf / .docx · mehrere Dateien möglich'}</span>
            <input type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" multiple onChange={(event) => setEvidence(Array.from(event.target.files ?? []))} />
          </label>
          <label className={`file-drop optional ${iamSnapshot ? 'selected' : ''}`}>
            <FileJson2 size={22} /><strong>IAM-Snapshot <em>optional</em></strong><span>{iamSnapshot?.name ?? '.json · CertaAccess Beispielschema'}</span>
            <input type="file" accept=".json,application/json" onChange={(event) => setIamSnapshot(event.target.files?.[0] ?? null)} />
          </label>
        </div>

        <div className="import-trust"><LockKeyhole size={17} /><span><strong>Untrusted by design.</strong> Dateityp, MIME-Type, Magic Bytes und Größe werden vor dem Parsen serverseitig geprüft.</span></div>
        {submitting && progress && (
          <div className="analysis-progress" role="status" aria-live="polite">
            <div><span>{progress.phase}</span><strong>{progress.percent}%</strong></div>
            <progress max="100" value={progress.percent}>{progress.percent}%</progress>
            <small>Die Analyse läuft als lokaler Hintergrundjob. Dieses Fenster bitte geöffnet lassen.</small>
          </div>
        )}
        {error && <div className="import-error" role="alert">{error}</div>}
        <div className="import-actions"><button type="button" className="button button-ghost" onClick={onClose} disabled={submitting}>Abbrechen</button><button className="button button-primary" disabled={submitting}>{submitting ? <LoaderCircle className="spin" size={16} /> : <Upload size={16} />}{submitting ? 'Analyse läuft …' : 'Analyse starten'}</button></div>
      </form>
    </div>
  );
}
