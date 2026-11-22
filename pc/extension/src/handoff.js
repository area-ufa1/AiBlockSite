const KEY_PREFIX = 'tabguardHandoff:';
const MAX_AGE_MS = 10 * 60 * 1000;

function key(nonce) {
  return `${KEY_PREFIX}${nonce}`;
}

function validNonce(nonce) {
  return typeof nonce === 'string' && /^[a-f0-9-]{20,100}$/i.test(nonce);
}

export function createHandoffStore(area, now = () => Date.now(), randomId = () => crypto.randomUUID()) {
  const claimed = new Set();
  return {
    async create(prompt, metadata = {}) {
      const nonce = randomId();
      const record = {
        nonce,
        prompt: String(prompt ?? ''),
        createdAt: now(),
        status: 'redirected',
        tabId: metadata.tabId ?? null,
        sourceUrl: String(metadata.sourceUrl ?? '').slice(0, 500),
        title: String(metadata.title ?? '').slice(0, 200),
        context: metadata.context ?? null,
      };
      await area.set({ [key(nonce)]: record });
      return record;
    },

    async get(nonce, tabId = null) {
      if (!validNonce(nonce)) return null;
      const result = await area.get(key(nonce));
      const record = result[key(nonce)];
      if (!record) return null;
      if (now() - record.createdAt > MAX_AGE_MS) {
        await area.remove(key(nonce));
        return null;
      }
      if (record.tabId !== null && tabId !== null && record.tabId !== tabId) return null;
      return record;
    },

    async claim(nonce, tabId = null) {
      if (claimed.has(nonce)) return null;
      const record = await this.get(nonce, tabId);
      if (!record) return null;
      claimed.add(nonce);
      return record;
    },

    async update(nonce, patch) {
      const record = await this.get(nonce);
      if (!record) return null;
      const next = { ...record, ...patch, nonce: record.nonce, prompt: record.prompt };
      await area.set({ [key(nonce)]: next });
      return next;
    },

    async remove(nonce) {
      if (validNonce(nonce)) {
        claimed.delete(nonce);
        await area.remove(key(nonce));
      }
    },
  };
}

export { KEY_PREFIX, MAX_AGE_MS };
