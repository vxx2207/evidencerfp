import {
  ArrowLeft,
  ArrowRight,
  BadgeCheck,
  CheckCircle2,
  FileWarning,
  Fingerprint,
  RotateCcw,
  ShieldAlert,
  Sparkles,
  X,
} from 'lucide-react';
import type { AppView } from '../types';

export type DemoTourStep = 'source' | 'conflict' | 'not-found' | 'finding' | 'export';

export const demoTourSteps: DemoTourStep[] = ['source', 'conflict', 'not-found', 'finding', 'export'];

const content: Record<DemoTourStep, {
  eyebrow: string;
  title: string;
  description: string;
  talkTrack: string;
  nextLabel?: string;
  icon: typeof Sparkles;
}> = {
  source: {
    eyebrow: 'Beleg · IAM-001',
    title: 'Belege statt Behauptungen.',
    description: 'Die Antwort öffnet direkt die vollständige Passage. Das exakte Zitat ist markiert und auf den abgerufenen Chunk zurückführbar.',
    talkTrack: '„Jede Aussage zeigt exakt die Textstelle, auf der sie basiert.“',
    nextLabel: 'Konflikt prüfen',
    icon: BadgeCheck,
  },
  conflict: {
    eyebrow: 'Konflikt · IAM-020',
    title: 'Widersprüche stoppen die Freigabe.',
    description: '365 oder 180 Tage? EvidenceRFP zeigt beide Angaben nebeneinander und lässt keine scheinbar sichere Antwort durch.',
    talkTrack: '„Das System verschweigt Unsicherheit nicht – es macht sie entscheidbar.“',
    nextLabel: 'Keinen Treffer zeigen',
    icon: FileWarning,
  },
  'not-found': {
    eyebrow: 'Klärung · IAM-022',
    title: 'Nicht gefunden heißt: nicht erfunden.',
    description: 'Ohne belastbaren C5-Nachweis bleibt die Antwort bewusst leer. Statt einer Halluzination entsteht eine konkrete interne Klärungsfrage.',
    talkTrack: '„Aus einer Wissenslücke wird direkt der nächste Presales-Schritt.“',
    nextLabel: 'IAM-Kontrolle öffnen',
    icon: Sparkles,
  },
  finding: {
    eyebrow: 'Kontrolle · finding-001',
    title: 'Dokumente treffen technische Realität.',
    description: 'Eine deterministische Regel erkennt ein privilegiertes Konto ohne phishing-resistente MFA – mit Objekt, Nachweis und Maßnahme.',
    talkTrack: '„Wir beantworten nicht nur den Fragebogen, sondern prüfen die Umgebung.“',
    nextLabel: 'Review abschließen',
    icon: ShieldAlert,
  },
  export: {
    eyebrow: 'Human Gate · IAM-004',
    title: 'Ein bewusster Klick bis zum Evidence Pack.',
    description: 'Nur diese Antwort ist noch offen. Nach deiner Freigabe wechselt die Tour automatisch zum Review – dann ist der XLSX-Export bereit.',
    talkTrack: '„Die KI entwirft. Der Mensch entscheidet. Erst dann wird exportiert.“',
    icon: CheckCircle2,
  },
};

export function DemoTour({
  step,
  view,
  openQuestions,
  onStep,
  onClose,
  onReset,
}: {
  step: DemoTourStep;
  view: AppView;
  openQuestions: number;
  onStep: (step: DemoTourStep) => void;
  onClose: () => void;
  onReset: () => void;
}) {
  const index = demoTourSteps.indexOf(step);
  const current = content[step];
  const Icon = current.icon;
  const reviewReady = step === 'export' && openQuestions === 0 && view === 'review';

  return (
    <aside className="demo-tour" aria-label="Geführte 60-Sekunden-Demo" aria-live="polite">
      <div className="demo-tour-topline">
        <span><Fingerprint size={13} /> 60-Sekunden-Demo</span>
        <span>{index + 1} / {demoTourSteps.length}</span>
        <button onClick={onClose} aria-label="Demo-Tour schließen"><X size={16} /></button>
      </div>

      <div className="demo-tour-progress" aria-label={`Schritt ${index + 1} von ${demoTourSteps.length}`}>
        {demoTourSteps.map((item, itemIndex) => <i key={item} className={itemIndex <= index ? 'active' : ''} />)}
      </div>

      <div className="demo-tour-copy">
        <span className="demo-tour-icon"><Icon size={19} /></span>
        <div>
          <span className="eyebrow">{reviewReady ? 'Export freigeschaltet' : current.eyebrow}</span>
          <h2>{reviewReady ? 'Review komplett. Evidence Pack bereit.' : current.title}</h2>
        </div>
        <p>{reviewReady ? 'Alle 24 Antworten sind bewusst entschieden. Der Export enthält Antworten, Findings, Quellen und Reviewstatus.' : current.description}</p>
        <blockquote>{current.talkTrack}</blockquote>
      </div>

      <div className="demo-tour-actions">
        <button className="demo-tour-reset" onClick={onReset}><RotateCcw size={14} /> Demo zurücksetzen</button>
        <span />
        {index > 0 && <button className="button button-ghost compact" onClick={() => onStep(demoTourSteps[index - 1])}><ArrowLeft size={14} /> Zurück</button>}
        {current.nextLabel && <button className="button button-primary compact" onClick={() => onStep(demoTourSteps[index + 1])}>{current.nextLabel} <ArrowRight size={14} /></button>}
        {reviewReady && <button className="button button-ghost compact" onClick={onClose}>Tour abschließen <CheckCircle2 size={14} /></button>}
      </div>
    </aside>
  );
}
