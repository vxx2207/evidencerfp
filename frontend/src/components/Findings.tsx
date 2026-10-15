import {
  AlertOctagon,
  ArrowUpRight,
  Braces,
  CheckCircle2,
  Clock3,
  Fingerprint,
  KeyRound,
  LockKeyhole,
  ScanLine,
  ShieldAlert,
  UserRoundX,
} from 'lucide-react';
import { useState } from 'react';
import { formatAnalysisDateTime } from '../format';
import type { Analysis, IamFinding } from '../types';
import { PageIntro } from './Chrome';

const severityLabel = { KRITISCH: 'Kritisch', HOCH: 'Hoch', MITTEL: 'Mittel' } as const;

function FindingIcon({ finding }: { finding: IamFinding }) {
  if (finding.code.includes('MFA')) return <Fingerprint size={23} />;
  if (finding.code.includes('LCM')) return <UserRoundX size={23} />;
  return <KeyRound size={23} />;
}

export function Findings({ analysis }: { analysis: Analysis }) {
  const [expandedFindingId, setExpandedFindingId] = useState<string | null>(null);
  const severityCounts = {
    critical: analysis.findings.filter((finding) => finding.severity === 'KRITISCH').length,
    high: analysis.findings.filter((finding) => finding.severity === 'HOCH').length,
    medium: analysis.findings.filter((finding) => finding.severity === 'MITTEL').length,
  };

  return (
    <div className="page findings-page">
      <PageIntro
        eyebrow="Identity Posture"
        title="IAM Findings"
        description={`Deterministische Kontrollen gegen den synthetischen ${analysis.customer}-Tenant – transparent, reproduzierbar und ohne KI-Interpretation.`}
        action={<div className="scan-stamp"><ScanLine size={18} /><span><strong>{analysis.status === 'COMPLETED' ? 'Scan vollständig' : `Scan ${analysis.progress} %`}</strong><small>{analysis.findings.length} Befunde · {formatAnalysisDateTime(analysis.createdAt)}</small></span></div>}
      />

      <section className="finding-summary">
        <div><span className="summary-icon critical"><AlertOctagon size={18} /></span><span><strong>{severityCounts.critical}</strong><small>Kritisch</small></span></div>
        <div><span className="summary-icon high"><ShieldAlert size={18} /></span><span><strong>{severityCounts.high}</strong><small>Hoch</small></span></div>
        <div><span className="summary-icon medium"><Clock3 size={18} /></span><span><strong>{severityCounts.medium}</strong><small>Mittel</small></span></div>
        <div className="coverage-stat"><span>Analysefortschritt</span><strong>{analysis.status === 'COMPLETED' ? 'Abgeschlossen' : `${analysis.progress} %`}</strong><div><i style={{ width: `${analysis.progress}%` }} /></div></div>
      </section>

      <section className="finding-grid">
        {analysis.findings.map((finding, index) => (
          <article key={finding.id} className={`finding-card severity-${finding.severity.toLowerCase()}`}>
            <div className="finding-card-top">
              <div className="finding-icon"><FindingIcon finding={finding} /></div>
              <div><span className="rule-code">{finding.code}</span><h2>{finding.rule}</h2></div>
              <span className={`severity-pill severity-${finding.severity.toLowerCase()}`}>{severityLabel[finding.severity]}</span>
            </div>
            <p className="finding-description">{finding.description}</p>
            <div className="affected-object"><span><Braces size={14} /> Betroffenes Objekt</span><code>{finding.object}</code></div>
            <div className="finding-evidence"><span>Kontrollnachweis</span><p>{finding.evidence}</p></div>
            <div className="finding-recommendation"><div><CheckCircle2 size={16} /><strong>Empfohlene Maßnahme</strong></div><p>{finding.recommendation}</p></div>
            {expandedFindingId === finding.id && <div className="finding-technical-details" id={`finding-details-${finding.id}`}><code>rule={finding.code}<br />severity={finding.severity}<br />object={finding.object}<br />control={finding.control}</code></div>}
            <div className="finding-footer"><span><LockKeyhole size={13} /> {finding.control}</span><button aria-expanded={expandedFindingId === finding.id} aria-controls={`finding-details-${finding.id}`} onClick={() => setExpandedFindingId((current) => current === finding.id ? null : finding.id)}>{expandedFindingId === finding.id ? 'Details schließen' : 'Technische Details'} <ArrowUpRight size={13} /></button></div>
            <span className="finding-number">0{index + 1}</span>
          </article>
        ))}
      </section>

      <section className="rule-engine-panel">
        <div className="rule-engine-mark"><span>IF</span><i /><span>THEN</span></div>
        <div><span className="eyebrow">Deterministic by design</span><h2>Drei Regeln. Keine Blackbox.</h2><p>Jedes Finding basiert auf einer nachvollziehbaren Bedingung im Tenant-Snapshot. Das Sprachmodell formuliert keine Risiken und führt keine Aktionen aus.</p></div>
        <div className="rule-code-sample"><code><span>when</span> account.privileged<br />&nbsp;&nbsp;&& !mfa.phishingResistant<br /><span>then</span> flag(<em>"IAM-MFA-01"</em>)</code></div>
      </section>
    </div>
  );
}
