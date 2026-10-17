import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

describe('EvidenceRFP portfolio demo', () => {
  beforeEach(() => window.history.replaceState(null, '', '/'));

  it('zeigt Analysekennzahlen und öffnet die Frage-Queue', async () => {
    const user = userEvent.setup();
    render(<App />);

    expect(await screen.findByRole('heading', { name: /Von 24 Fragen zu/i })).toBeInTheDocument();
    expect(screen.getByText('17')).toBeInTheDocument();
    expect(screen.getByText('Evidenzabdeckung')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Frage-Queue, 1 offen/i })).toBeInTheDocument();
    expect(screen.getByText('35 h')).toBeInTheDocument();
    expect(screen.getByText('38,2 %')).toBeInTheDocument();
    await user.click(screen.getByText('Annahmen anzeigen'));
    expect(screen.getByText(/12.000 € Einführung plus 900 € Betrieb/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Frage-Queue/i }));
    expect(await screen.findByRole('heading', { name: 'Frage-Queue' })).toBeInTheDocument();
    expect(screen.getByText('IAM-024')).toBeInTheDocument();
  });

  it('öffnet eine validierte Zitation in der Dokumentansicht', async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole('heading', { name: /Von 24 Fragen zu/i });
    await user.click(screen.getByRole('button', { name: /Answer Workbench/i }));
    const sourceButtons = await screen.findAllByRole('button', { name: /CertaAccess Security Architecture/i });
    await user.click(sourceButtons[0]);

    const drawer = await screen.findByRole('dialog', { name: /CertaAccess Security Architecture/i });
    expect(within(drawer).getByText(/Zitat serverseitig validiert/i)).toBeInTheDocument();
    expect(within(drawer).getByText(/Für privilegierte Rollen kann eine Richtlinie/i)).toBeInTheDocument();
  });

  it('verhindert die Freigabe bei einem Quellenkonflikt', async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole('heading', { name: /Von 24 Fragen zu/i });
    await user.click(screen.getByRole('button', { name: /Frage-Queue/i }));
    await user.click(await screen.findByText('IAM-020'));

    expect(await screen.findByText('Widersprüchliche Aufbewahrungsfrist')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Freigeben/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Blockieren/i })).toBeDisabled();
  });

  it('durchsucht die tatsächlich geladenen Fragen', async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole('heading', { name: /Von 24 Fragen zu/i });
    await user.click(screen.getByRole('button', { name: /Fragen durchsuchen/i }));
    await user.type(screen.getByRole('textbox', { name: 'Suche' }), 'Hardware Security Module');
    await user.click(screen.getByRole('button', { name: /IAM-023/i }));

    expect(await screen.findByRole('heading', { name: /Kann der Kunde ein eigenes Hardware Security Module betreiben/i })).toBeInTheDocument();
  });

  it('kopiert eine interne Klärungsfrage in die Zwischenablage', async () => {
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
    render(<App />);

    await screen.findByRole('heading', { name: /Von 24 Fragen zu/i });
    await user.click(screen.getByRole('button', { name: /Frage-Queue/i }));
    await user.click(await screen.findByText('IAM-022'));
    await user.click(await screen.findByRole('button', { name: 'In Zwischenablage kopieren' }));

    expect(writeText).toHaveBeenCalledWith('Compliance-Team nach Prüfbericht, Geltungsbereich und Berichtszeitraum fragen.');
    expect(screen.getByRole('button', { name: 'Kopiert' })).toBeInTheDocument();
  });

  it('startet die geführte Demo direkt mit der vollständigen Quellpassage', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole('button', { name: '60-Sekunden-Demo starten' }));

    expect(window.location.hash).toBe('#tour/source');
    expect(await screen.findByRole('complementary', { name: 'Geführte 60-Sekunden-Demo' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Belege statt Behauptungen.' })).toBeInTheDocument();
    expect(await screen.findByRole('dialog', { name: /CertaAccess Security Architecture/i })).toBeInTheDocument();
  });

  it('führt über Konflikt, Klärung und Finding bis zur letzten Review-Entscheidung', async () => {
    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole('button', { name: '60-Sekunden-Demo starten' }));
    await user.click(screen.getByRole('button', { name: 'Konflikt prüfen' }));
    expect(window.location.hash).toBe('#tour/conflict');
    expect(await screen.findByText('Widersprüchliche Aufbewahrungsfrist')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Keinen Treffer zeigen' }));
    expect(window.location.hash).toBe('#tour/not-found');
    expect(await screen.findByText(/Compliance-Team nach Prüfbericht/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'IAM-Kontrolle öffnen' }));
    expect(window.location.hash).toBe('#tour/finding');
    expect(await screen.findByText(/rule=IAM-MFA-01/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Review abschließen' }));
    expect(window.location.hash).toBe('#tour/export');
    expect(await screen.findByRole('heading', { name: /Sind zeitlich begrenzte Zugriffsrechte/i })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Freigeben' }));

    expect(await screen.findByRole('heading', { name: 'Review abgeschlossen.' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'XLSX exportieren' })).toBeEnabled();
    expect(screen.getByRole('heading', { name: 'Review komplett. Evidence Pack bereit.' })).toBeInTheDocument();
  }, 10_000);

  it('öffnet einen Tour-Schritt direkt per Hash-Link und setzt gespeicherte Entscheidungen zurück', async () => {
    const user = userEvent.setup();
    window.history.replaceState(null, '', '/#tour/export');
    render(<App />);

    expect(await screen.findByRole('heading', { name: /Sind zeitlich begrenzte Zugriffsrechte/i })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Freigeben' }));
    expect(await screen.findByRole('heading', { name: 'Review abgeschlossen.' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Demo zurücksetzen' }));
    expect(await screen.findByRole('heading', { name: /Von 24 Fragen zu/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Frage-Queue, 1 offen/i })).toBeInTheDocument();
    expect(window.location.hash).toBe('');
  });
});
