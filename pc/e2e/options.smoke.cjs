// Options page scenarios; required by smoke.cjs after the blocking scenarios (2 blocks counted).
module.exports = async ({ context, extId, open, waitFor, check, CLAUDE }) => {
  const page = await context.newPage();
  page.on('console', (m) => { if (m.type() === 'error') console.log('options error: ' + m.text()); });
  page.on('pageerror', (e) => console.log('options pageerror: ' + e.message));
  await page.goto(`chrome-extension://${extId}/options/options.html`);

  await check('options show the default category with its count', async () => {
    await page.locator('.category', { hasText: 'Порно' }).locator('text=Заблокировано: 2').waitFor({ timeout: 5000 });
  });

  await check('status line is shown', async () => {
    await page.locator('#status', { hasText: 'Защита работает' }).waitFor({ timeout: 5000 });
  });

  await check('a category added in the options page blocks', async () => {
    await page.click('#add');
    await page.fill('#name', 'Тест');
    await page.fill('#keywords', 'zzzword');
    await page.click('#save');
    await page.locator('.category', { hasText: 'Тест' }).waitFor({ timeout: 5000 });
    const p = await context.newPage();
    await open(p, 'https://example.com/zzzword');
    await waitFor(() => p.url().startsWith(CLAUDE), 10000, p.url());
  });

  await check('a disabled category does not block', async () => {
    await page.locator('.category', { hasText: 'Тест' }).locator('input.toggle').uncheck();
    await new Promise((r) => setTimeout(r, 500));
    const p = await context.newPage();
    await open(p, 'https://example.com/zzzword/again');
    await new Promise((r) => setTimeout(r, 2500));
    if (p.url().startsWith(CLAUDE)) throw new Error('blocked by a disabled category');
  });

  await check('save is disabled for an empty name', async () => {
    await page.click('#add');
    await page.fill('#name', '   ');
    if (!(await page.isDisabled('#save'))) throw new Error('save enabled');
    await page.click('#cancel');
  });
};
