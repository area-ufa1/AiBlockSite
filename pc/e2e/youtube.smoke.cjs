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

  // Fake YouTube pages: the player response carries the uploader's category, like the real site.
  const player = (id, genre) => `{videoDetails:{videoId:'${id}'},microformat:{playerMicroformatRenderer:{category:'${genre}'}}}`;
  const watchPage = (id, genre, extra = '') => `<title>Я купил остров - YouTube</title>
    <script>var ytInitialPlayerResponse = ${player(id, genre)};</script>
    <div id="movie_player"></div><div id="secondary"><div id="related">рекомендации</div></div>${extra}`;
  await context.route(/youtube\.com\/(watch|$|\?)/, (route) => {
    const url = new URL(route.request().url());
    const id = url.searchParams.get('v');
    let body;
    if (url.pathname === '/') {
      body = '<title>YouTube</title><ytd-browse page-subtype="home"><ytd-rich-grid-renderer id="grid">лента</ytd-rich-grid-renderer></ytd-browse>';
    } else if (id === 'spa') {
      // Starts on a study video, then navigates in-page like YouTube does (no reload).
      body = watchPage('spa', 'Education', `<script>
        setTimeout(() => {
          document.getElementById('movie_player').getPlayerResponse = () => (${player('fun2', 'Gaming')});
          history.pushState({}, '', '/watch?v=fun2');
          document.dispatchEvent(new Event('yt-navigate-finish'));
        }, 2500);</script>`);
    } else {
      body = watchPage(id, id.startsWith('fun') ? 'Entertainment' : 'Education');
    }
    route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body });
  });

  await check('entertainment video with a neutral title goes to claude', async () => {
    const p = await context.newPage();
    await open(p, 'https://www.youtube.com/watch?v=fun1');
    await waitFor(() => p.url().startsWith(CLAUDE), 10000, p.url());
  });

  await check('education video with the same title stays', async () => {
    const p = await context.newPage();
    await open(p, 'https://www.youtube.com/watch?v=edu1');
    await new Promise((r) => setTimeout(r, 3000));
    if (p.url().startsWith(CLAUDE)) throw new Error('blocked');
  });

  await check('in-page navigation to an entertainment video goes to claude', async () => {
    const p = await context.newPage();
    await open(p, 'https://www.youtube.com/watch?v=spa');
    await new Promise((r) => setTimeout(r, 1500));
    if (p.url().startsWith(CLAUDE)) throw new Error('study video blocked');
    await waitFor(() => p.url().startsWith(CLAUDE), 10000, p.url());
  });

  await check('recommendations are hidden', async () => {
    const p = await context.newPage();
    await open(p, 'https://www.youtube.com/watch?v=edu3');
    await waitFor(() => p.evaluate(() => getComputedStyle(document.getElementById('related')).display === 'none'), 5000, 'related visible');
    await open(p, 'https://www.youtube.com/');
    await waitFor(() => p.evaluate(() => getComputedStyle(document.getElementById('grid')).display === 'none'), 5000, 'home feed visible');
  });
};

