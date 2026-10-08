// Returning to a blocked page must block it again.
module.exports = async ({ context, open, waitFor, check, CLAUDE }) => {
  await check('back to a page blocked by its title blocks again', async () => {
    const p = await context.newPage();
    await open(p, 'https://example.org/titled/back');
    await waitFor(() => p.url().startsWith(CLAUDE), 10000, `first block: ${p.url()}`);
    await new Promise((r) => setTimeout(r, 2500));
    await p.goBack({ waitUntil: 'commit' }).catch(() => {});
    await new Promise((r) => setTimeout(r, 500));
    await waitFor(() => p.url().startsWith(CLAUDE), 10000, `after back: ${p.url()}`);
  });

  await check('revisiting a blocked address right away blocks again', async () => {
    const p = await context.newPage();
    await open(p, 'https://example.org/search?q=porn-again');
    await waitFor(() => p.url().startsWith(CLAUDE), 10000, `first block: ${p.url()}`);
    await open(p, 'https://example.org/search?q=porn-again');
    await new Promise((r) => setTimeout(r, 300));
    await waitFor(() => p.url().startsWith(CLAUDE), 10000, `revisit: ${p.url()}`);
  });

  await check('cyrillic search is blocked before load', async () => {
    const p = await context.newPage();
    await open(p, 'https://example.org/search?q=порно');
    await waitFor(() => p.url().startsWith(CLAUDE), 10000, p.url());
  });
};
