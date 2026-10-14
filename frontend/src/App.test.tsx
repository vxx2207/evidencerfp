import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('EvidenceRFP portfolio demo', () => {
  it('zeigt Analysekennzahlen und öffnet die Frage-Queue', async () => {
    const user = userEvent.setup();
    render(<App />);

    expect(await screen.findByRole('heading', { name: /Von 24 Fragen zu/i })).toBeInTheDocument();
    expect(screen.getByText('17')).toBeInTheDocument();
    expect(screen.getByText('Evidenzabdeckung')).toBeInTheDocument();

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
  });
});
