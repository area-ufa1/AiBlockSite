// Setup wizard and protection against weakening. Runs last: it turns protection on.
module.exports = async ({ context, extId, waitFor, check }) => {
  const [worker] = context.serviceWorkers();
  const storage = (key) => worker.evaluate((k) => chrome.storage.local.get(k).then((r) => r[k]), key);
  const options = `chrome-extension://${extId}/options/options.html`;
  const porn = (p) => p.locator('.category', { hasText: 'Порно' }).locator('input.toggle');

  await check('setup wizard opens on install', async () => {
    await waitFor(() => context.pages().some((p) => p.url().endsWith('/options/setup.html')), 5000, 'no setup tab');
  });

  await check('setup wizard saves pause mode', async () => {
    const p = context.pages().find((x) => x.url().endsWith('/options/setup.html'));
    await p.check('input[name=mode][value=pause]');
    await p.selectOption('#pause-minutes', '5');
    await p.click('#finish');
    await waitFor(async () => (await storage('lock'))?.mode === 'pause', 5000, 'lock not saved');
    if ((await storage('lock')).pauseMinutes !== 5) throw new Error('minutes');
  });

  await check('toggle off is refused while locked', async () => {
    const p = await context.newPage();
    await p.goto(options);
    await porn(p).waitFor();
    await porn(p).click();
    await p.locator('#unlock[open]').waitFor({ timeout: 5000 });
    if (!(await p.isDisabled('#start-countdown'))) throw new Error('pause can start without the phrase');
    await p.locator('#phrase').pressSequentially('Я осознанно ослабляю свою защиту');
    if (await p.isDisabled('#start-countdown')) throw new Error('typed phrase not accepted');
    if (!(await p.locator('footer #weaken').count())) throw new Error('weaken button is not in the footer');
    if (!(await porn(p).isChecked())) throw new Error('toggle stayed off');
    const cats = await storage('categories');
    if (!cats.find((c) => c.id === 'porn').enabled) throw new Error('disabled in storage');
  });

  await check('after the countdown the toggle works', async () => {
    await worker.evaluate(async () => {
      const { lock } = await chrome.storage.local.get('lock');
      await chrome.storage.local.set({ lock: { ...lock, countdownStartedAt: Date.now() - 6 * 60_000 } });
    });
    const p = await context.newPage();
    await p.goto(options);
    await p.locator('#lock-status', { hasText: 'открыты' }).waitFor({ timeout: 5000 });
    await porn(p).click();
    await waitFor(async () => !(await storage('categories')).find((c) => c.id === 'porn').enabled, 5000, 'not disabled');
    await porn(p).click(); // enabling back is always allowed
  });

  await check('partner pin unlocks', async () => {
    const s = await context.newPage();
    await s.goto(`chrome-extension://${extId}/options/setup.html`);
    await s.check('input[name=mode][value=partner]');
    await s.fill('#pin', '1234');
    await s.fill('#pin2', '1234');
    await s.click('#finish');
    await waitFor(async () => (await storage('lock'))?.mode === 'partner', 5000, 'partner not saved');

    const p = await context.newPage();
    await p.goto(options);
    await porn(p).waitFor();
    await porn(p).click();
    await p.locator('#unlock[open]').waitFor({ timeout: 5000 });
    await p.fill('#pin-input', '0000');
    await p.click('#pin-ok');
    await p.locator('#pin-error', { hasText: 'Неверный' }).waitFor({ timeout: 5000 });
    await p.fill('#pin-input', '1234');
    await p.click('#pin-ok');
    await p.locator('#lock-status', { hasText: 'открыты' }).waitFor({ timeout: 5000 });
    await porn(p).click();
    await waitFor(async () => !(await storage('categories')).find((c) => c.id === 'porn').enabled, 5000, 'not disabled');
  });

  await check('version and update link are shown', async () => {
    const p = await context.newPage();
    await p.goto(options);
    await p.locator('#version', { hasText: '.' }).waitFor({ timeout: 5000 });
    const href = await p.getAttribute('#updates', 'href');
    if (href !== 'https://github.com/area-ufa1/AiBlockSite/releases/latest') throw new Error(href);
  });
};
