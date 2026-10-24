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

  /**
   * Built-in categories are seeded once each (`seeded` remembers which): a new install gets all,
   * an older install gains the ones added later, and a category the user deleted is not re-added.
   */
  async function read() {
    const { categories, stats, seeded } = await area.get(['categories', 'stats', 'seeded']);
    // Installs from before `seeded` existed were seeded with «Порно» only.
    const known = new Set(seeded ?? (categories === undefined ? [] : ['porn']));
    const missing = DEFAULT_CATEGORIES.filter((d) => !known.has(d.id));
    let list = categories ?? [];
    if (missing.length > 0 || seeded === undefined) {
      list = [...list, ...structuredClone(missing).filter((d) => !list.some((c) => c.id === d.id))];
      await area.set({ categories: list, seeded: DEFAULT_CATEGORIES.map((d) => d.id) });
    }
    return { categories: list, stats: stats ?? {} };
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
      const created = { id: crypto.randomUUID(), enabled: true, bundledList: null, urlPrefixes: [], onlyHosts: [], ...fields };
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
