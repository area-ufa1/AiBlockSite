// Forces the search engines' own safe mode, so explicit images and videos disappear from results
// even for queries no keyword catches.

const ENGINES = [
  { host: /^(www\.)?google\.[a-z.]+$/, path: /^\/search$/, param: 'safe', value: 'active' },
  { host: /^(www\.)?(yandex\.[a-z.]+|ya\.ru)$/, path: /^\/(images\/|video\/)?search\/?$/, param: 'family', value: 'yes' },
  { host: /^(www\.)?bing\.com$/, path: /^\/(images\/|videos\/)?search$/, param: 'adlt', value: 'strict' },
  { host: /^(www\.)?duckduckgo\.com$/, path: /^\/$/, param: 'kp', value: '1', needs: 'q' },
];

/** The same search with safe mode forced, or null if `url` is not a search or is already safe. */
export function safeSearchUrl(url) {
  let u;
  try {
    u = new URL(url);
  } catch {
    return null;
  }
  if (u.protocol !== 'https:' && u.protocol !== 'http:') return null;
  const engine = ENGINES.find((e) => e.host.test(u.hostname) && e.path.test(u.pathname));
  if (!engine || (engine.needs && !u.searchParams.has(engine.needs))) return null;
  if (u.searchParams.get(engine.param) === engine.value) return null;
  u.searchParams.set(engine.param, engine.value);
  return u.href;
}
