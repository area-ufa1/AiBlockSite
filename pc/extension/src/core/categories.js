import { extractHost, normalize } from './text.js';

export const DEFAULT_CATEGORIES = Object.freeze([
  Object.freeze({
    id: 'porn',
    name: 'Порно',
    enabled: true,
    keywords: ['порно', 'porn', 'xxx', 'секс', 'sex', 'хентай', 'hentai', 'nsfw', 'onlyfans', 'эротика'],
    domains: [],
    bundledList: 'adult',
  }),
]);

/** Keywords typed by the user: one per line or comma-separated; normalized, no blanks, no duplicates. */
export function parseKeywords(text) {
  return [...new Set(text.split(/[\n,]/).map(normalize).filter((s) => s.length > 0))];
}

/**
 * Domains typed by the user, reduced to hosts (`https://www.x.com/` → `x.com`); non-hosts dropped.
 * Cyrillic domains are stored in punycode, the form browsers report (`порно.рф` → `xn--…`).
 */
export function parseDomains(text) {
  return [...new Set(parseKeywords(text).map(extractHost).filter((h) => h !== null).map(toAscii))];
}

function toAscii(host) {
  try {
    return new URL(`http://${host}`).hostname;
  } catch {
    return host;
  }
}
