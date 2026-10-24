// Takes the screenshots for the instruction page from the packaged extension.
// Run: NODE_PATH=/opt/node22/lib/node_modules node site/screenshots.cjs
const { chromium } = require('playwright');
const { execFileSync } = require('node:child_process');
const path = require('node:path');
const fs = require('node:fs');
const os = require('node:os');

const root = path.resolve(__dirname, '..');
const ext = path.join(root, 'pc/dist/TabGuard');
const out = path.join(__dirname, 'img');

(async () => {
  execFileSync('bash', [path.join(root, 'pc/scripts/package.sh')], { stdio: 'ignore' });
  fs.mkdirSync(out, { recursive: true });
  const context = await chromium.launchPersistentContext(fs.mkdtempSync(path.join(os.tmpdir(), 'tabguard-shots-')), {
    headless: true,
    ignoreDefaultArgs: ['--disable-extensions'],
    executablePath: process.env.CHROMIUM || '/opt/pw-browsers/chromium',
    args: ['--headless=new', `--disable-extensions-except=${ext}`, `--load-extension=${ext}`],
    viewport: { width: 900, height: 900 },
    locale: 'ru-RU',
  });
  try {
    let [worker] = context.serviceWorkers();
    if (!worker) worker = await context.waitForEvent('serviceworker', { timeout: 10000 });
    const id = new URL(worker.url()).host;
    const page = await context.newPage();

    await page.goto(`chrome-extension://${id}/options/setup.html`);
    await page.check('#cat-youtube');
    await page.waitForTimeout(300);
    await page.screenshot({ path: path.join(out, 'setup.png'), fullPage: true });

    await page.click('#finish');
    await page.waitForSelector('#done:not([hidden])');
    await page.goto(`chrome-extension://${id}/options/options.html`);
    await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(out, 'options.png'), fullPage: true });
    console.log('screenshots saved to', out);
  } finally {
    await context.close();
  }
})().catch((e) => { console.error(e); process.exit(1); });
