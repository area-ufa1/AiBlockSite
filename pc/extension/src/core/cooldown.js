/** Blocks the same tab and URL at most once per `ms` (before-load and title checks both fire). */
export class BlockCooldown {
  #last = new Map();

  constructor(ms = 2000, now = () => Date.now()) {
    this.ms = ms;
    this.now = now;
  }

  shouldBlock(tabId, url) {
    const key = `${tabId}|${url}`;
    const t = this.now();
    const prev = this.#last.get(key);
    if (prev !== undefined && t - prev < this.ms) return false;
    this.#last.set(key, t);
    if (this.#last.size > 500) this.#last.delete(this.#last.keys().next().value);
    return true;
  }

  /** Undo a recorded block that did not happen (e.g. the tab could not be updated), so a later check retries. */
  forget(tabId, url) {
    this.#last.delete(`${tabId}|${url}`);
  }
}
