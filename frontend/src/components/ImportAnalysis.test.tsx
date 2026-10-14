import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ImportAnalysis } from './ImportAnalysis';

describe('ImportAnalysis', () => {
  it('fordert RFP und Evidenz an, bevor ein Lauf gestartet wird', async () => {
    const user = userEvent.setup();
    render(<ImportAnalysis open onClose={() => undefined} onSubmit={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'Analyse starten' }));

    expect(screen.getByRole('alert')).toHaveTextContent('XLSX-Fragebogen');
  });

  it('übergibt die ausgewählten lokalen Dateien', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const { container } = render(<ImportAnalysis open onClose={() => undefined} onSubmit={onSubmit} />);
    const inputs = container.querySelectorAll<HTMLInputElement>('input[type="file"]');
    const rfp = new File(['xlsx'], 'rfp.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const evidence = new File(['pdf'], 'security.pdf', { type: 'application/pdf' });

    await user.upload(inputs[0], rfp);
    await user.upload(inputs[1], evidence);
    await user.click(screen.getByRole('button', { name: 'Analyse starten' }));

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ rfp, evidence: [evidence] }));
  });
});
