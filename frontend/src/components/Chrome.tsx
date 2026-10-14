import {
  Archive,
  BarChart3,
  Bell,
  BookOpenText,
  ChevronDown,
  ClipboardCheck,
  FileQuestion,
  Menu,
  PanelLeftClose,
  Search,
  ShieldAlert,
  Sparkles,
  X,
} from 'lucide-react';
import type { AppView } from '../types';

interface NavItem {
  id: AppView;
  label: string;
  icon: typeof BarChart3;
  count?: number;
}

export function Sidebar({
  view,
  onNavigate,
  mobileOpen,
  onMobileClose,
  openQuestions,
  findings,
}: {
  view: AppView;
  onNavigate: (view: AppView) => void;
  mobileOpen: boolean;
  onMobileClose: () => void;
  openQuestions: number;
  findings: number;
}) {
  const items: NavItem[] = [
    { id: 'dashboard', label: 'Übersicht', icon: BarChart3 },
    { id: 'questions', label: 'Frage-Queue', icon: FileQuestion, count: openQuestions },
    { id: 'workbench', label: 'Answer Workbench', icon: Sparkles },
    { id: 'findings', label: 'IAM Findings', icon: ShieldAlert, count: findings },
    { id: 'evidence', label: 'Evidence Library', icon: BookOpenText },
    { id: 'review', label: 'Review & Export', icon: ClipboardCheck },
  ];

  function navigate(target: AppView) {
    onNavigate(target);
    onMobileClose();
  }

  return (
    <>
      {mobileOpen && <button className="sidebar-scrim" onClick={onMobileClose} aria-label="Navigation schließen" />}
      <aside className={`sidebar ${mobileOpen ? 'sidebar-open' : ''}`}>
        <div className="brand-lockup">
          <div className="brand-mark" aria-hidden="true"><span>ER</span></div>
          <div>
            <strong>Evidence<span>RFP</span></strong>
            <small>by CertaAccess</small>
          </div>
          <button className="icon-button sidebar-close" onClick={onMobileClose} aria-label="Navigation schließen"><X size={18} /></button>
        </div>

        <div className="demo-seal">
          <span className="live-dot" />
          <div><strong>Synthetische Demo</strong><small>Keine echten Kundendaten</small></div>
        </div>

        <nav aria-label="Hauptnavigation">
          <p className="nav-label">Workspace</p>
          {items.map((item) => {
            const Icon = item.icon;
            return (
              <button key={item.id} className={`nav-item ${view === item.id ? 'active' : ''}`} onClick={() => navigate(item.id)} aria-current={view === item.id ? 'page' : undefined}>
                <Icon size={18} aria-hidden="true" />
                <span>{item.label}</span>
                {item.count !== undefined && <span className="nav-count">{item.count}</span>}
              </button>
            );
          })}
        </nav>

        <div className="sidebar-spacer" />
        <div className="security-note">
          <div className="security-note-icon"><Archive size={16} /></div>
          <div><strong>Local-first</strong><span>Uploads verlassen nie deine Umgebung.</span></div>
        </div>
        <button className="profile-card">
          <span className="avatar">MK</span>
          <span><strong>Mara König</strong><small>Solution Engineer</small></span>
          <ChevronDown size={15} />
        </button>
      </aside>
    </>
  );
}

export function Topbar({
  pageTitle,
  customer,
  mode,
  onMenu,
  onSearch,
}: {
  pageTitle: string;
  customer: string;
  mode: 'demo' | 'api';
  onMenu: () => void;
  onSearch: () => void;
}) {
  return (
    <header className="topbar">
      <button className="icon-button mobile-menu" onClick={onMenu} aria-label="Navigation öffnen"><Menu size={20} /></button>
      <div className="breadcrumbs">
        <span>{customer}</span><span className="slash">/</span><strong>{pageTitle}</strong>
      </div>
      <div className="topbar-actions">
        <button className="search-trigger" onClick={onSearch}><Search size={16} /><span>Fragen durchsuchen</span><kbd>⌘ K</kbd></button>
        <span className={`mode-pill ${mode}`}><span />{mode === 'demo' ? 'Demo-Modus' : 'Live API'}</span>
        <button className="icon-button notification-button" aria-label="Benachrichtigungen"><Bell size={18} /><span /></button>
      </div>
    </header>
  );
}

export function GlobalSearch({ open, onClose, onNavigate }: { open: boolean; onClose: () => void; onNavigate: (view: AppView) => void }) {
  if (!open) return null;
  return (
    <div className="modal-shell" role="dialog" aria-modal="true" aria-label="Schnellsuche">
      <button className="modal-backdrop" onClick={onClose} aria-label="Suche schließen" />
      <div className="command-palette">
        <Search size={20} />
        <input autoFocus placeholder="Frage, Dokument oder Finding suchen …" aria-label="Suche" />
        <kbd>ESC</kbd>
        <div className="command-results">
          <p>Schnellzugriff</p>
          <button onClick={() => { onNavigate('questions'); onClose(); }}><FileQuestion size={17} /><span><strong>Offene Fragen prüfen</strong><small>12 Antworten warten auf Review</small></span><span>→</span></button>
          <button onClick={() => { onNavigate('findings'); onClose(); }}><ShieldAlert size={17} /><span><strong>IAM Findings öffnen</strong><small>3 Kontrollen benötigen Aufmerksamkeit</small></span><span>→</span></button>
          <button onClick={() => { onNavigate('evidence'); onClose(); }}><BookOpenText size={17} /><span><strong>Evidence Library</strong><small>5 verifizierte Quelldokumente</small></span><span>→</span></button>
        </div>
      </div>
    </div>
  );
}

export function Toast({ message, onClose }: { message: string | null; onClose: () => void }) {
  if (!message) return null;
  return <div className="toast" role="status"><ClipboardCheck size={18} /><span>{message}</span><button onClick={onClose} aria-label="Hinweis schließen"><X size={15} /></button></div>;
}

export function PageIntro({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: React.ReactNode }) {
  return (
    <div className="page-intro">
      <div><span className="eyebrow">{eyebrow}</span><h1>{title}</h1><p>{description}</p></div>
      {action && <div className="page-action">{action}</div>}
    </div>
  );
}

export function DecorativeGrid() {
  return <div className="decorative-grid" aria-hidden="true"><PanelLeftClose size={16} /></div>;
}
