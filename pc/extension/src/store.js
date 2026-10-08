import { DEFAULT_CATEGORIES, parseDomains, parseKeywords } from './core/categories.js';

/**
 * Categories and per-category block counts in `area` (chrome.storage.local or a stand-in).
 * Writes are serialized so two blocks at once do not lose a count.
 */
export function createStore(area) {
  let queue = Promise.resolve();
  const serial = (fn) => {
    const run = queue.then(fn);
    queue = run.catch(() => {});
    return run;
  };

  async function read() {
    const { categories, stats } = await area.get(['categories', 'stats']);
    if (categories === undefined) {
      const seeded = structuredClone(DEFAULT_CATEGORIES);
      await area.set({ categories: seeded });
      return { categories: seeded, stats: stats ?? {} };
    }
    return { categories, stats: stats ?? {} };
  }

  return {
    getState: () => serial(read),

    saveCategory: (id, name, keywordsText, domainsText) => serial(async () => {
      const { categories } = await read();
      const fields = { name: name.trim(), keywords: parseKeywords(keywordsText), domains: parseDomains(domainsText) };
      const existing = id === null ? undefined : categories.find((c) => c.id === id);
      if (existing) {
        Object.assign(existing, fields);
        await area.set({ categories });
        return existing.id;
      }
      const created = { id: crypto.randomUUID(), enabled: true, bundledList: null, ...fields };
      await area.set({ categories: [...categories, created] });
      return created.id;
    }),

    setEnabled: (id, enabled) => serial(async () => {
      const { categories } = await read();
      await area.set({ categories: categories.map((c) => (c.id === id ? { ...c, enabled } : c)) });
    }),

    deleteCategory: (id) => serial(async () => {
      const { categories, stats } = await read();
      delete stats[id];
      await area.set({ categories: categories.filter((c) => c.id !== id), stats });
    }),

    incrementStat: (id) => serial(async () => {
      const { stats } = await read();
      stats[id] = (stats[id] ?? 0) + 1;
      await area.set({ stats });
    }),
  };
}
