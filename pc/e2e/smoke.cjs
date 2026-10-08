// End-to-end smoke test: loads the packaged extension into Chromium and checks blocking.
// Run: NODE_PATH=/opt/node22/lib/node_modules node pc/e2e/smoke.cjs
const { chromium } = require('playwright');
const { execFileSync } = require('node:child_process');
const path = require('node:path');
const fs = require('node:fs');
const os = require('node:os');

const root = path.resolve(__dirname, '../..');
const ext = path.join(root, 'pc/dist/TabGuard');
const CLAUDE = 'https://claude.ai/new?q=';

async function waitFor(fn, ms, label) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    if (await fn()) return;
    await new Promise((r) => setTimeout(r, 200));
  }
  throw new Error(`timeout: ${label}`);
}

async function open(page, url) {
  await page.goto(url, { waitUntil: 'commit', timeout: 10000 }).catch(() => {});
}

(async () => {
  execFileSync('bash', [path.join(root, 'pc/scripts/package.sh')], { stdio: 'ignore' });
  const userDir = fs.mkdtempSync(path.join(os.tmpdir(), 'tabguard-'));
  const context = await chromium.launchPersistentContext(userDir, {
    headless: true,
    ignoreDefaultArgs: ['--disable-extensions'],
    executablePath: process.env.CHROMIUM || '/opt/pw-browsers/chromium',
    args: ['--headless=new', `--disable-extensions-except=${ext}`, `--load-extension=${ext}`],
  });
  // No internet in CI/containers: answer every request locally. Pages under /titled/ get a "bad" title.
  await context.route('**/*', (route) => {
    const url = route.request().url();
    const title = url.includes('/titled/') ? 'Горячее порно видео' : 'Обычная страница';
    route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: `<title>${title}</title><p>ok</p>` });
  });
  let failed = false;
  const check = async (name, fn) => {
    try { await fn(); console.log(`PASS: ${name}`); } catch (e) { failed = true; console.log(`FAIL: ${name}: ${e.message}`); }
  };
  try {
    let [worker] = context.serviceWorkers();
    if (!worker) worker = await context.waitForEvent('serviceworker', { timeout: 10000 });
    const extId = new URL(worker.url()).host;
    globalThis.smoke = { context, extId, open, waitFor, check, CLAUDE };

    const page = await context.newPage();
    await check('search with a keyword goes to claude', async () => {
      await open(page, 'https://example.com/search?q=porn');
      await waitFor(() => page.url().startsWith(CLAUDE), 10000, page.url());
    });
    await check('ordinary page is untouched', async () => {
      const p = await context.newPage();
      await open(p, 'https://example.com/');
      await new Promise((r) => setTimeout(r, 3000));
      if (p.url().startsWith(CLAUDE)) throw new Error(p.url());
    });
    await check('page with a bad title goes to claude', async () => {
      const p = await context.newPage();
      await open(p, 'https://example.com/titled/page');
      await waitFor(() => p.url().startsWith(CLAUDE), 10000, p.url());
    });
    await check('claude page itself is never blocked again', async () => {
      await new Promise((r) => setTimeout(r, 2500));
      if (!page.url().startsWith(CLAUDE)) throw new Error(page.url());
    });
    await check('blocks are counted', async () => {
      const { stats } = await worker.evaluate(() => chrome.storage.local.get('stats'));
      if (stats?.porn !== 2) throw new Error(JSON.stringify(stats));
    });

    const extra = path.join(__dirname, 'options.smoke.cjs');
    if (fs.existsSync(extra)) await require(extra)(globalThis.smoke);
  } finally {
    await context.close();
  }
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
