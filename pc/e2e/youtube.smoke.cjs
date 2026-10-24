// YouTube category: shorts by address, entertainment videos by title, study videos pass.
module.exports = async ({ context, open, waitFor, check, CLAUDE }) => {
  const [worker] = context.serviceWorkers();
  await worker.evaluate(async () => {
    const { categories } = await chrome.storage.local.get('categories');
    await chrome.storage.local.set({ categories: categories.map((c) => (c.id === 'youtube-fun' ? { ...c, enabled: true } : c)) });
  });

  await check('youtube shorts go to claude', async () => {
    const p = await context.newPage();
    await open(p, 'https://www.youtube.com/shorts/abc123');
    await waitFor(() => p.url().startsWith(CLAUDE), 10000, p.url());
    if (!decodeURIComponent(p.url()).includes('развлекательный YouTube')) throw new Error('wrong prompt');
  });

  await check('study video on youtube is not blocked', async () => {
    const p = await context.newPage();
    await open(p, 'https://www.youtube.com/watch?v=study');
    await new Promise((r) => setTimeout(r, 3000));
    if (p.url().startsWith(CLAUDE)) throw new Error('blocked');
  });
};
