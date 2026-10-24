import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  initialLockState, isUnlocked, settle, startCountdown, cancelCountdown, remainingCountdownMs,
  setPauseMode, setPartnerMode, tryPin, isWeakening,
} from '../extension/src/core/lock.js';

const MIN = 60_000;
const SALT = '00112233445566778899aabbccddeeff';
const cat = (over = {}) => ({ id: 'c', name: 'C', enabled: true, keywords: ['a', 'b'], domains: ['x.com'], bundledList: null, urlPrefixes: ['y.com/s/'], onlyHosts: [], ...over });

test('unconfigured lock is open', () => assert.equal(isUnlocked(initialLockState(), 0), true));

test('pause mode is locked until the countdown ends, then open for 5 minutes', () => {
  let s = setPauseMode(initialLockState(), 20);
  assert.equal(isUnlocked(s, 0), false);
  s = startCountdown(s, 0);
  assert.equal(remainingCountdownMs(s, 5 * MIN), 15 * MIN);
  assert.equal(isUnlocked(settle(s, 19 * MIN), 19 * MIN), false);
  s = settle(s, 20 * MIN);
  assert.equal(isUnlocked(s, 20 * MIN), true);
  assert.equal(isUnlocked(s, 24.9 * MIN), true);
});

test('countdown survives restart', () => {
  const s = startCountdown(setPauseMode(initialLockState(), 5), 1000);
  const later = settle(structuredClone(s), 1000 + 6 * MIN); // state reloaded from storage, no ticks in between
  assert.equal(isUnlocked(later, 1000 + 6 * MIN), true);
});

test('window closes after 5 minutes', () => {
  const s = settle(startCountdown(setPauseMode(initialLockState(), 5), 0), 5 * MIN);
  assert.equal(isUnlocked(s, 10 * MIN + 1), false);
});

test('cancel countdown keeps it locked', () => {
  const s = cancelCountdown(startCountdown(setPauseMode(initialLockState(), 5), 0));
  assert.equal(isUnlocked(settle(s, 30 * MIN), 30 * MIN), false);
  assert.equal(remainingCountdownMs(s, 0), null);
});

test('correct pin opens the window; wrong does not', async () => {
  const s = await setPartnerMode(initialLockState(), '1234', SALT);
  assert.equal(isUnlocked(s, 0), false);
  assert.notEqual(s.pinHash, '1234');
  const bad = await tryPin(s, '0000', 0);
  assert.equal(bad.ok, false);
  const good = await tryPin(bad.state, '1234', 0);
  assert.equal(good.ok, true);
  assert.equal(isUnlocked(good.state, 4 * MIN), true);
  assert.equal(good.state.failedAttempts, 0);
});

test('pin lockout ends after 30 minutes', async () => {
  let s = await setPartnerMode(initialLockState(), '1234', SALT);
  for (let i = 0; i < 5; i++) s = (await tryPin(s, '9999', 0)).state;
  const blocked = await tryPin(s, '1234', 29 * MIN);
  assert.equal(blocked.ok, false);
  const after = await tryPin(blocked.state, '1234', 30 * MIN + 1);
  assert.equal(after.ok, true);
});

test('pause minutes outside 5..60 rejected; pin must be 4-8 digits', async () => {
  assert.throws(() => setPauseMode(initialLockState(), 4));
  assert.throws(() => setPauseMode(initialLockState(), 61));
  await assert.rejects(setPartnerMode(initialLockState(), '123', SALT));
  await assert.rejects(setPartnerMode(initialLockState(), '123456789', SALT));
  await assert.rejects(setPartnerMode(initialLockState(), '12a4', SALT));
});

test('weakening rules', () => {
  assert.equal(isWeakening(cat(), null), true, 'delete');
  assert.equal(isWeakening(cat(), cat({ enabled: false })), true, 'disable');
  assert.equal(isWeakening(cat(), cat({ keywords: ['a'] })), true, 'removed word');
  assert.equal(isWeakening(cat(), cat({ domains: [] })), true, 'removed domain');
  assert.equal(isWeakening(cat(), cat({ urlPrefixes: [] })), true, 'removed prefix');
  assert.equal(isWeakening(cat(), cat({ onlyHosts: ['youtube.com'] })), true, 'words narrowed to a host');
  assert.equal(isWeakening(cat({ onlyHosts: ['a.com', 'b.com'] }), cat({ onlyHosts: ['a.com'] })), true, 'scope narrowed');
  assert.equal(isWeakening(cat({ onlyHosts: ['a.com'] }), cat({ onlyHosts: [] })), false, 'scope widened to everywhere');
  assert.equal(isWeakening(cat({ enabled: false }), cat()), false, 'enable');
  assert.equal(isWeakening(cat(), cat({ keywords: ['a', 'b', 'c'], name: 'New' })), false, 'add word, rename');
  assert.equal(isWeakening(null, cat()), false, 'create');
});
