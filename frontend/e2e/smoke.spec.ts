import { expect, test } from '@playwright/test';

test('durchläuft den zentralen EvidenceRFP-Demo-Pfad', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Von 24 Fragen zu/i })).toBeVisible();
  await expect(page.getByText('Synthetische Demo')).toBeVisible();

  await page.getByRole('button', { name: /Frage-Queue/i }).click();
  await expect(page.getByRole('heading', { name: 'Frage-Queue' })).toBeVisible();
  await page.getByText('IAM-020').click();

  await expect(page.getByText('Widersprüchliche Aufbewahrungsfrist')).toBeVisible();
  await expect(page.getByRole('button', { name: /Freigeben/i })).toBeDisabled();
  await page.getByRole('button', { name: /Trust & Compliance Overview/i }).first().click();
  await expect(page.getByRole('dialog', { name: /Trust & Compliance Overview/i })).toBeVisible();
  await expect(page.getByText('Zitat serverseitig validiert')).toBeVisible();
});
