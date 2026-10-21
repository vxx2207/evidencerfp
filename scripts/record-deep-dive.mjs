import { spawn } from 'node:child_process';
import { copyFile, mkdir, rm } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, '..');
const frontendDirectory = join(repositoryRoot, 'frontend');
const outputPath = join(repositoryRoot, 'docs', 'assets', 'evidencerfp-deep-dive-4m.webm');
const baseUrl = process.env.EVIDENCERFP_DEMO_URL ?? 'http://127.0.0.1:4173';
const dryRun = process.argv.includes('--dry-run');
const timeScale = dryRun ? 0.02 : 1;
const requireFromFrontend = createRequire(join(frontendDirectory, 'package.json'));
const { chromium } = requireFromFrontend('playwright');

const timeline = {
  pipeline: 25_000,
  source: 45_000,
  conflict: 80_000,
  notFound: 115_000,
  finding: 145_000,
  export: 180_000,
  approve: 197_000,
  download: 208_000,
  value: 220_000,
  reset: 232_000,
  end: 240_000,
};

const captions = {
  problem: {
    title: 'Problem und Ziel',
    text: 'Security-Fragebögen kosten Presales Zeit. EvidenceRFP erstellt belegte Entwürfe statt freier KI-Antworten.',
  },
  pipeline: {
    title: 'Synthetische Demo · lokale Verarbeitung',
    text: '24 Fragen, fünf Produktdokumente und ein IAM-Snapshot durchlaufen Retrieval, Regelprüfungen und Human Review.',
  },
  source: {
    title: 'Belegte Antwort · IAM-001',
    text: 'Ein Klick öffnet die vollständige Passage. Quelle, Chunk-ID und Zitat werden serverseitig geprüft.',
  },
  conflict: {
    title: 'Quellenkonflikt · IAM-020',
    text: '365 und 180 Tage stehen nebeneinander. Der Widerspruch bleibt sichtbar und sperrt die Freigabe.',
  },
  notFound: {
    title: 'Bewusstes Nichtwissen · IAM-022',
    text: 'Fehlt der Nachweis, bleibt die Antwort NOT_FOUND. Statt einer Halluzination entsteht eine interne Klärungsfrage.',
  },
  finding: {
    title: 'Deterministische IAM-Kontrolle',
    text: 'Regeln finden fehlende phishing-resistente MFA, verwaiste Privilegien und alte Service-Secrets – ohne LLM-Entscheidung.',
  },
  export: {
    title: 'Human Gate und Evidence Pack',
    text: 'Der Sales Engineer entscheidet die letzte offene Frage. Erst danach wird der XLSX-Export mit vier prüfbaren Blättern aktiv.',
  },
  value: {
    title: 'Geschäftswert',
    text: 'Das synthetische ROI-Modell legt Annahmen offen: schnelleres Research, weniger unbelegte Zusagen und ein auditierbares Ergebnis.',
  },
};

function scaled(milliseconds) {
  return Math.max(0, Math.round(milliseconds * timeScale));
}

async function isReachable(url) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(1_000) });
    return response.ok;
  } catch {
    return false;
  }
}

async function waitForServer(url, processHandle) {
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    if (await isReachable(url)) return;
    if (processHandle?.exitCode !== null) throw new Error(`Vite wurde vorzeitig mit Code ${processHandle.exitCode} beendet.`);
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 250));
  }
  throw new Error(`Demo unter ${url} wurde nicht innerhalb von 30 Sekunden erreichbar.`);
}

async function startDemoServer() {
  if (await isReachable(baseUrl)) return null;
  const viteEntry = requireFromFrontend.resolve('vite');
  const viteCli = resolve(dirname(viteEntry), '..', '..', 'bin', 'vite.js');
  const child = spawn(process.execPath, [viteCli, '--host', '127.0.0.1', '--port', '4173'], {
    cwd: frontendDirectory,
    env: { ...process.env, VITE_APP_MODE: 'demo' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  child.stdout.on('data', (chunk) => process.stdout.write(`[vite] ${chunk}`));
  child.stderr.on('data', (chunk) => process.stderr.write(`[vite] ${chunk}`));
  await waitForServer(baseUrl, child);
  return child;
}

async function injectPresentationLayer(page) {
  await page.evaluate(() => {
    const style = document.createElement('style');
    style.textContent = `
      #deep-dive-caption {
        position: fixed;
        left: 32px;
        right: 32px;
        bottom: 24px;
        z-index: 2147483646;
        box-sizing: border-box;
        padding: 16px 20px;
        border: 1px solid rgba(94, 234, 212, .42);
        border-radius: 14px;
        background: rgba(4, 15, 25, .92);
        box-shadow: 0 18px 45px rgba(0, 0, 0, .35);
        color: #f8fafc;
        font: 500 20px/1.35 Inter, ui-sans-serif, system-ui, sans-serif;
        pointer-events: none;
      }
      #deep-dive-caption strong {
        display: block;
        margin-bottom: 4px;
        color: #5eead4;
        font-size: 13px;
        letter-spacing: .12em;
        text-transform: uppercase;
      }
      #deep-dive-cursor {
        position: fixed;
        z-index: 2147483647;
        width: 22px;
        height: 22px;
        margin: -11px 0 0 -11px;
        border: 3px solid #5eead4;
        border-radius: 999px;
        background: rgba(15, 118, 110, .24);
        box-shadow: 0 0 0 5px rgba(94, 234, 212, .14);
        opacity: 0;
        transition: left .42s ease, top .42s ease, opacity .2s ease, transform .16s ease;
        pointer-events: none;
      }
      .deep-dive-focus {
        outline: 3px solid #5eead4 !important;
        outline-offset: 4px !important;
      }
    `;
    document.head.append(style);

    const caption = document.createElement('div');
    caption.id = 'deep-dive-caption';
    caption.setAttribute('role', 'presentation');
    caption.innerHTML = '<strong>EvidenceRFP Deep Dive</strong><span></span>';
    document.body.append(caption);

    const cursor = document.createElement('div');
    cursor.id = 'deep-dive-cursor';
    document.body.append(cursor);
  });
}

async function setCaption(page, caption) {
  await page.evaluate(({ title, text }) => {
    const root = document.querySelector('#deep-dive-caption');
    if (!root) return;
    const heading = root.querySelector('strong');
    const body = root.querySelector('span');
    if (heading) heading.textContent = title;
    if (body) body.textContent = text;
  }, caption);
}

async function emphasizeAndClick(page, locator) {
  await locator.scrollIntoViewIfNeeded();
  const box = await locator.boundingBox();
  if (box) {
    await page.evaluate(({ x, y }) => {
      const cursor = document.querySelector('#deep-dive-cursor');
      if (!(cursor instanceof HTMLElement)) return;
      cursor.style.left = `${x}px`;
      cursor.style.top = `${y}px`;
      cursor.style.opacity = '1';
    }, { x: box.x + box.width / 2, y: box.y + box.height / 2 });
  }
  await locator.evaluate((element) => element.classList.add('deep-dive-focus'));
  await page.waitForTimeout(scaled(800));
  await locator.click();
  await page.evaluate(() => {
    const cursor = document.querySelector('#deep-dive-cursor');
    if (cursor instanceof HTMLElement) cursor.style.transform = 'scale(.65)';
  });
  await page.waitForTimeout(scaled(220));
  await page.evaluate(() => {
    const cursor = document.querySelector('#deep-dive-cursor');
    if (cursor instanceof HTMLElement) cursor.style.transform = 'scale(1)';
    document.querySelectorAll('.deep-dive-focus').forEach((element) => element.classList.remove('deep-dive-focus'));
  });
}

async function recordDeepDive() {
  const server = await startDemoServer();
  const recordingDirectory = join(tmpdir(), `evidencerfp-deep-dive-${process.pid}`);
  await mkdir(recordingDirectory, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    acceptDownloads: true,
    locale: 'de-DE',
    viewport: { width: 1440, height: 900 },
    recordVideo: { dir: recordingDirectory, size: { width: 1440, height: 900 } },
  });
  const page = await context.newPage();
  const video = page.video();

  try {
    await page.goto(baseUrl, { waitUntil: 'networkidle' });
    await page.getByRole('heading', { name: /Von 24 Fragen zu/i }).waitFor();
    await injectPresentationLayer(page);

    const startedAt = Date.now();
    const waitUntil = async (milliseconds) => {
      const remaining = scaled(milliseconds) - (Date.now() - startedAt);
      if (remaining > 0) await page.waitForTimeout(remaining);
    };

    await setCaption(page, captions.problem);
    await waitUntil(timeline.pipeline);
    await setCaption(page, captions.pipeline);

    await waitUntil(timeline.source);
    await setCaption(page, captions.source);
    await emphasizeAndClick(page, page.getByRole('button', { name: '60-Sekunden-Demo starten' }));
    await page.getByRole('dialog', { name: /CertaAccess Security Architecture/i }).waitFor();

    const tour = page.getByRole('complementary', { name: 'Geführte 60-Sekunden-Demo' });
    await waitUntil(timeline.conflict);
    await setCaption(page, captions.conflict);
    await emphasizeAndClick(page, tour.getByRole('button', { name: 'Konflikt prüfen' }));
    await page.getByText('Widersprüchliche Aufbewahrungsfrist').waitFor();

    await waitUntil(timeline.notFound);
    await setCaption(page, captions.notFound);
    await emphasizeAndClick(page, tour.getByRole('button', { name: 'Keinen Treffer zeigen' }));
    await page.getByText(/Compliance-Team nach Prüfbericht/i).waitFor();

    await waitUntil(timeline.finding);
    await setCaption(page, captions.finding);
    await emphasizeAndClick(page, tour.getByRole('button', { name: 'IAM-Kontrolle öffnen' }));
    await page.getByText(/rule=IAM-MFA-01/i).waitFor();

    await waitUntil(timeline.export);
    await setCaption(page, captions.export);
    await emphasizeAndClick(page, tour.getByRole('button', { name: 'Review abschließen' }));
    await page.getByText('Human Gate · IAM-004', { exact: true }).waitFor();

    await waitUntil(timeline.approve);
    await emphasizeAndClick(page, page.getByRole('button', { name: 'Freigeben' }));
    await page.getByRole('heading', { name: 'Review abgeschlossen.' }).waitFor();

    await waitUntil(timeline.download);
    const downloadPromise = page.waitForEvent('download');
    await emphasizeAndClick(page, page.getByRole('button', { name: 'XLSX exportieren' }));
    await downloadPromise;

    await waitUntil(timeline.value);
    await setCaption(page, captions.value);

    await waitUntil(timeline.reset);
    await emphasizeAndClick(page, tour.getByRole('button', { name: 'Demo zurücksetzen' }));
    await page.getByRole('heading', { name: /Von 24 Fragen zu/i }).waitFor();
    await waitUntil(timeline.end);
  } finally {
    await context.close();
    await browser.close();
    if (server && server.exitCode === null) server.kill();
  }

  if (!video) throw new Error('Playwright hat kein Videoobjekt erzeugt.');
  const recordedPath = await video.path();
  if (dryRun) {
    await rm(recordingDirectory, { recursive: true, force: true });
    console.log('Dry-Run erfolgreich: Tour, Klickpfad und Videoaufnahme wurden validiert.');
    return;
  }

  await mkdir(dirname(outputPath), { recursive: true });
  await copyFile(recordedPath, outputPath);
  await rm(recordingDirectory, { recursive: true, force: true });
  console.log(`Deep-Dive gespeichert: ${outputPath}`);
}

recordDeepDive().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
