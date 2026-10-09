/** Lines of a bundled list file: trimmed, without blanks and `#` comments. */
export function parseKeywordList(text) {
  return text.split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('#'));
}

/**
 * Lazily loads bundled domain lists (one host per line, `#` comments) into Sets.
 * Results are cached for the life of the service worker; a failed read gives an empty Set
 * and is retried next time instead of being cached.
 */
export function createBundledLoader(fetchText) {
  const cache = new Map();
  return {
    async get(name) {
      if (cache.has(name)) return cache.get(name);
      try {
        const text = await fetchText(name);
        const set = new Set(parseKeywordList(text));
        cache.set(name, set);
        return set;
      } catch {
        return new Set();
      }
    },
  };
}
