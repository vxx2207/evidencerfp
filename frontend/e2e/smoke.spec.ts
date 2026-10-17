import { expect, test } from '@playwright/test';

test('durchläuft den zentralen EvidenceRFP-Demo-Pfad', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Von 24 Fragen zu/i })).toBeVisible();
  await expect(page.getByText('Synthetische Demo')).toBeVisible();

  await page.getByRole('button', { name: '60-Sekunden-Demo starten' }).click();
  await expect(page).toHaveURL(/#tour\/source$/);
  await expect(page.getByRole('dialog', { name: /CertaAccess Security Architecture/i })).toBeVisible();
  const tour = page.getByRole('complementary', { name: 'Geführte 60-Sekunden-Demo' });

  await tour.getByRole('button', { name: 'Konflikt prüfen' }).click();
  await expect(page).toHaveURL(/#tour\/conflict$/);
  await expect(page.getByText('Widersprüchliche Aufbewahrungsfrist')).toBeVisible();
  await expect(page.getByRole('button', { name: /Freigeben/i })).toBeDisabled();

  await tour.getByRole('button', { name: 'Keinen Treffer zeigen' }).click();
  await expect(page).toHaveURL(/#tour\/not-found$/);
  await expect(page.getByText(/Compliance-Team nach Prüfbericht/i)).toBeVisible();

  await tour.getByRole('button', { name: 'IAM-Kontrolle öffnen' }).click();
  await expect(page).toHaveURL(/#tour\/finding$/);
  await expect(page.getByText(/rule=IAM-MFA-01/i)).toBeVisible();

  await tour.getByRole('button', { name: 'Review abschließen' }).click();
  await expect(page).toHaveURL(/#tour\/export$/);
  await expect(page.getByText('IAM-004')).toBeVisible();
  await page.getByRole('button', { name: 'Freigeben' }).click();

  await expect(page.getByRole('heading', { name: 'Review abgeschlossen.' })).toBeVisible();
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'XLSX exportieren' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/Evidence_Pack\.xlsx$/);

  await tour.getByRole('button', { name: 'Demo zurücksetzen' }).click();
  await expect(page.getByRole('heading', { name: /Von 24 Fragen zu/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /Frage-Queue, 1 offen/i })).toBeVisible();
  await expect(page).not.toHaveURL(/#tour\//);
});

test('öffnet jeden Tour-Schritt als direkten Deep Link', async ({ page }) => {
  const cases = [
    ['source', 'Belege statt Behauptungen.'],
    ['conflict', 'Widersprüche stoppen die Freigabe.'],
    ['not-found', 'Nicht gefunden heißt: nicht erfunden.'],
    ['finding', 'Dokumente treffen technische Realität.'],
    ['export', 'Ein bewusster Klick bis zum Evidence Pack.'],
  ] as const;

  for (const [step, heading] of cases) {
    await page.goto(`/#tour/${step}`);
    await expect(page.getByRole('heading', { name: heading })).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`#tour/${step}$`));
  }
});
