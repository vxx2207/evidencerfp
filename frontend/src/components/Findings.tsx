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
import type { Analysis, IamFinding } from '../types';
import { PageIntro } from './Chrome';

const severityLabel = { KRITISCH: 'Kritisch', HOCH: 'Hoch', MITTEL: 'Mittel' } as const;

function FindingIcon({ finding }: { finding: IamFinding }) {
  if (finding.code.includes('MFA')) return <Fingerprint size={23} />;
  if (finding.code.includes('LCM')) return <UserRoundX size={23} />;
  return <KeyRound size={23} />;
}

export function Findings({ analysis }: { analysis: Analysis }) {
  return (
    <div className="page findings-page">
      <PageIntro
        eyebrow="Identity Posture"
        title="IAM Findings"
        description="Deterministische Kontrollen gegen den synthetischen Nordstern-Tenant – transparent, reproduzierbar und ohne KI-Interpretation."
        action={<div className="scan-stamp"><ScanLine size={18} /><span><strong>Scan vollständig</strong><small>2.418 Identitäten · 08:43 Uhr</small></span></div>}
      />

      <section className="finding-summary">
        <div><span className="summary-icon critical"><AlertOctagon size={18} /></span><span><strong>1</strong><small>Kritisch</small></span></div>
        <div><span className="summary-icon high"><ShieldAlert size={18} /></span><span><strong>1</strong><small>Hoch</small></span></div>
        <div><span className="summary-icon medium"><Clock3 size={18} /></span><span><strong>1</strong><small>Mittel</small></span></div>
        <div className="coverage-stat"><span>Kontrollabdeckung</span><strong>3 / 3 Regeln ausgeführt</strong><div><i /></div></div>
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
            <div className="finding-footer"><span><LockKeyhole size={13} /> {finding.control}</span><button>Technische Details <ArrowUpRight size={13} /></button></div>
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
