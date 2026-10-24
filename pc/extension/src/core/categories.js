import { extractHost, normalize } from './text.js';
import { normalizeForPrefix } from './matcher.js';

export const DEFAULT_CATEGORIES = Object.freeze([
  Object.freeze({
    id: 'porn',
    name: 'Порно',
    enabled: true,
    keywords: ['порно', 'porn', 'xxx', 'секс', 'sex', 'хентай', 'hentai', 'nsfw', 'onlyfans', 'эротика'],
    domains: [],
    bundledList: 'adult',
    urlPrefixes: [],
    onlyHosts: [],
  }),
  Object.freeze({
    id: 'youtube-fun',
    name: 'YouTube: развлечения',
    enabled: false,
    keywords: [],
    domains: [],
    bundledList: 'youtube-fun',
    urlPrefixes: ['youtube.com/shorts/'],
    onlyHosts: ['youtube.com', 'youtu.be'],
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

/** Address prefixes typed by the user (`https://www.vk.com/clips` → `vk.com/clips`); blanks dropped. */
export function parseUrlPrefixes(text) {
  return [...new Set(text.split(/[\n,]/).map((l) => normalizeForPrefix(l)).filter((l) => l.length > 0))];
}
