// Protection against weakening the blocker: either a pause before changes ("pause")
// or a PIN known to a partner ("partner"). Pure state transitions; the caller stores the state.

const MIN = 60_000;
export const WINDOW_MS = 5 * MIN;
export const PIN_LOCKOUT_MS = 30 * MIN;
export const MAX_PIN_ATTEMPTS = 5;

/** Typed (not pasted) before a pause can start: a moment to notice what is happening. */
export const PAUSE_PHRASE = 'Я осознанно ослабляю свою защиту';

const simplify = (s) => s.toLowerCase().replaceAll('ё', 'е').replace(/[^\p{L}\p{N}]+/gu, ' ').trim();

/** Case, spacing and punctuation do not matter. */
export function phraseMatches(text) {
  return simplify(text ?? '') === simplify(PAUSE_PHRASE);
}

/** @typedef {{mode: 'pause'|'partner'|null, pauseMinutes: number, pinHash: string|null, salt: string|null,
 *   countdownStartedAt: number|null, unlockedUntil: number, failedAttempts: number, pinLockedUntil: number}} LockState */

export function initialLockState() {
  return { mode: null, pauseMinutes: 20, pinHash: null, salt: null, countdownStartedAt: null, unlockedUntil: 0, failedAttempts: 0, pinLockedUntil: 0 };
}

/** Changes that weaken protection are allowed: not configured yet, or inside the window. */
export function isUnlocked(state, now) {
  return state.mode === null || now < state.unlockedUntil;
}

/** Turns a finished countdown into an open window (works after any gap, e.g. a restart). */
export function settle(state, now) {
  if (state.countdownStartedAt === null) return state;
  const end = state.countdownStartedAt + state.pauseMinutes * MIN;
  if (now < end) return state;
  return { ...state, countdownStartedAt: null, unlockedUntil: end + WINDOW_MS };
}

export function startCountdown(state, now) {
  if (state.mode !== 'pause') throw new Error('not in pause mode');
  return { ...state, countdownStartedAt: state.countdownStartedAt ?? now };
}

export function cancelCountdown(state) {
  return { ...state, countdownStartedAt: null };
}

/** Milliseconds left until the window opens, or null if no countdown runs. */
export function remainingCountdownMs(state, now) {
  if (state.countdownStartedAt === null) return null;
  return Math.max(0, state.countdownStartedAt + state.pauseMinutes * MIN - now);
}

export function setPauseMode(state, minutes) {
  if (!Number.isInteger(minutes) || minutes < 5 || minutes > 60) throw new Error('pause must be 5..60 minutes');
  return { ...state, mode: 'pause', pauseMinutes: minutes, pinHash: null, salt: null, countdownStartedAt: null, unlockedUntil: 0 };
}

export async function hashPin(salt, pin) {
  const bytes = new TextEncoder().encode(salt + pin);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export async function setPartnerMode(state, pin, salt) {
  if (!/^\d{4,8}$/.test(pin)) throw new Error('PIN must be 4-8 digits');
  return {
    ...state, mode: 'partner', pinHash: await hashPin(salt, pin), salt,
    countdownStartedAt: null, unlockedUntil: 0, failedAttempts: 0, pinLockedUntil: 0,
  };
}

/** @returns {Promise<{state: LockState, ok: boolean}>} */
export async function tryPin(state, pin, now) {
  if (state.mode !== 'partner' || now < state.pinLockedUntil) return { state, ok: false };
  if ((await hashPin(state.salt, pin)) === state.pinHash) {
    return { state: { ...state, unlockedUntil: now + WINDOW_MS, failedAttempts: 0, pinLockedUntil: 0 }, ok: true };
  }
  const failedAttempts = state.failedAttempts + 1;
  if (failedAttempts >= MAX_PIN_ATTEMPTS) return { state: { ...state, failedAttempts: 0, pinLockedUntil: now + PIN_LOCKOUT_MS }, ok: false };
  return { state: { ...state, failedAttempts }, ok: false };
}

const covers = (after = [], before = []) => before.every((x) => after.includes(x));

/** Hosts the category's words apply on: null = everywhere. */
const wordScope = (c) => ((c.onlyHosts ?? []).length === 0 ? null : c.onlyHosts);

/**
 * True if saving `after` over `before` weakens protection: deleting, disabling, removing
 * words/domains/prefixes, or narrowing where the words apply. Adding and enabling are free.
 */
export function isWeakening(before, after) {
  if (before === null) return false;
  if (after === null) return true;
  if (before.enabled && !after.enabled) return true;
  if (!covers(after.keywords, before.keywords) || !covers(after.domains, before.domains)) return true;
  if (!covers(after.urlPrefixes, before.urlPrefixes)) return true;
  const was = wordScope(before);
  const now = wordScope(after);
  if (now === null) return false;
  return was === null || !covers(now, was);
}
