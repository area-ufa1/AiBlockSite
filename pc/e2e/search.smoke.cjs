// Built-in dictionary and forced safe search.
module.exports = async ({ context, open, waitFor, check, CLAUDE }) => {
  await check('built-in dictionary blocks a search for "голые"', async () => {
    const p = await context.newPage();
    await open(p, 'https://www.google.com/search?q=голые');
    await waitFor(() => p.url().startsWith(CLAUDE), 10000, p.url());
  });

  await check('ordinary google search gets safe=active', async () => {
    const p = await context.newPage();
    await open(p, 'https://www.google.com/search?q=cats');
    await waitFor(() => p.url().includes('safe=active'), 10000, p.url());
    if (p.url().startsWith(CLAUDE)) throw new Error('blocked');
  });

  await check('yandex search gets family=yes', async () => {
    const p = await context.newPage();
    await open(p, 'https://yandex.ru/search/?text=котики');
    await waitFor(() => p.url().includes('family=yes'), 10000, p.url());
  });
};
