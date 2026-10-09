// On-device matching by category domains and keywords; same rules as the Android app (LocalMatcher.kt).
import { extractHost, tokenize } from './text.js';

const ALLOWED_HOSTS = ['claude.ai', 'anthropic.com'];

const isWithin = (host, domain) => host === domain || host.endsWith(`.${domain}`);

/** "a.b.com" → ["a.b.com", "b.com", "com"]. */
function suffixes(host) {
  const out = [host];
  for (let i = host.indexOf('.'); i !== -1; i = host.indexOf('.', i + 1)) out.push(host.slice(i + 1));
  return out;
}

function containsPrefixSequence(tokens, kw) {
  for (let i = 0; i + kw.length <= tokens.length; i++) {
    if (kw.every((part, j) => tokens[i + j].startsWith(part))) return true;
  }
  return false;
}

export function isAllowlisted(url) {
  const host = url ? extractHost(url) : null;
  return host !== null && ALLOWED_HOSTS.some((d) => isWithin(host, d));
}

/**
 * First enabled category the tab falls under: domains of all categories first, then keywords.
 * `category.domains` and the bundled sets must hold normalized hosts; `bundledKeywords` maps a
 * category's `bundledList` to built-in words that apply on top of the user's own.
 * @returns {{categoryId: string, categoryName: string, source: 'domain'|'keyword'} | null}
 */
export function matchTab(tab, categories, bundled, bundledKeywords = new Map()) {
  const enabled = categories.filter((c) => c.enabled);
  const result = (c, source) => ({ categoryId: c.id, categoryName: c.name, source });

  const host = tab.url ? extractHost(tab.url) : null;
  if (host !== null) {
    const hostSuffixes = suffixes(host);
    for (const c of enabled) {
      const own = new Set(c.domains);
      const list = c.bundledList ? bundled.get(c.bundledList) : undefined;
      if (hostSuffixes.some((s) => own.has(s) || list?.has(s))) return result(c, 'domain');
    }
  }

  const texts = [tab.title, tab.url].filter((t) => t).map(tokenize);
  for (const c of enabled) {
    const builtIn = (c.bundledList && bundledKeywords.get(c.bundledList)) || [];
    const keywords = [...c.keywords, ...builtIn].map(tokenize).filter((kw) => kw.length > 0);
    if (keywords.some((kw) => texts.some((tokens) => containsPrefixSequence(tokens, kw)))) return result(c, 'keyword');
  }
  return null;
}
