import { DEFAULT_CATEGORIES, parseDomains, parseKeywords, parseUrlPrefixes } from './core/categories.js';
import { initialLockState, isUnlocked, isWeakening, settle } from './core/lock.js';

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

  async function readLock(now) {
    const { lock } = await area.get(['lock']);
    return settle(lock ?? initialLockState(), now);
  }

  /** Rejects with Error('locked') if the change weakens protection and no window is open. */
  async function guard(before, after, now) {
    if (!isWeakening(before, after)) return;
    if (!isUnlocked(await readLock(now), now)) throw new Error('locked');
  }

  return {
    getState: () => serial(read),

    /** Protection state, with a finished countdown already turned into an open window. */
    getLock: (now = Date.now()) => serial(() => readLock(now)),

    saveLock: (lock) => serial(() => area.set({ lock })),

    /** `prefixesText`/`onlyHostsText` = null keeps the stored value. */
    saveCategory: (id, name, keywordsText, domainsText, prefixesText = null, onlyHostsText = null, now = Date.now()) => serial(async () => {
      const { categories } = await read();
      const fields = { name: name.trim(), keywords: parseKeywords(keywordsText), domains: parseDomains(domainsText) };
      if (prefixesText !== null) fields.urlPrefixes = parseUrlPrefixes(prefixesText);
      if (onlyHostsText !== null) fields.onlyHosts = parseDomains(onlyHostsText);
      const existing = id === null ? undefined : categories.find((c) => c.id === id);
      if (existing) {
        await guard(existing, { ...existing, ...fields }, now);
        Object.assign(existing, fields);
        await area.set({ categories });
        return existing.id;
      }
      const created = { id: crypto.randomUUID(), enabled: true, bundledList: null, urlPrefixes: [], onlyHosts: [], ...fields };
      await area.set({ categories: [...categories, created] });
      return created.id;
    }),

    setEnabled: (id, enabled, now = Date.now()) => serial(async () => {
      const { categories } = await read();
      const before = categories.find((c) => c.id === id);
      if (before) await guard(before, { ...before, enabled }, now);
      await area.set({ categories: categories.map((c) => (c.id === id ? { ...c, enabled } : c)) });
    }),

    deleteCategory: (id, now = Date.now()) => serial(async () => {
      const { categories, stats } = await read();
      const before = categories.find((c) => c.id === id);
      if (before) await guard(before, null, now);
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
