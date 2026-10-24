import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createStore } from '../extension/src/store.js';

/** In-memory stand-in for chrome.storage.local. */
function fakeArea(initial = {}) {
  const data = structuredClone(initial);
  return {
    data,
    async get(keys) {
      const out = {};
      for (const k of [keys].flat()) if (k in data) out[k] = structuredClone(data[k]);
      return out;
    },
    async set(obj) { Object.assign(data, structuredClone(obj)); },
  };
}

test('first run seeds default category', async () => {
  const area = fakeArea();
  const state = await createStore(area).getState();
  assert.equal(state.categories[0].name, 'Порно');
  assert.deepEqual(state.stats, {});
  assert.equal(area.data.categories[0].id, 'porn');
});

test('existing categories are not reseeded', async () => {
  const mine = { id: 'a', name: 'Моя', enabled: true, keywords: ['x'], domains: [], bundledList: null };
  const state = await createStore(fakeArea({ categories: [mine], stats: { a: 3 } })).getState();
  assert.deepEqual(state.categories[0], mine);
  assert.deepEqual(state.categories.map((c) => c.id), ['a', 'youtube-fun']); // deleted «Порно» is not re-added
  assert.deepEqual(state.stats, { a: 3 });
});

test('saveCategory creates a new enabled category with parsed input', async () => {
  const store = createStore(fakeArea());
  const id = await store.saveCategory(null, '  Казино ', 'Casino,\n\nСтавки', 'https://www.Bet.com/x');
  const c = (await store.getState()).categories.find((x) => x.id === id);
  assert.deepEqual({ ...c, id: undefined }, { id: undefined, name: 'Казино', enabled: true, keywords: ['casino', 'ставки'], domains: ['bet.com'], bundledList: null, urlPrefixes: [], onlyHosts: [] });
});

test('saveCategory keeps enabled and bundledList of existing', async () => {
  const store = createStore(fakeArea());
  await store.setEnabled('porn', false);
  await store.saveCategory('porn', 'Порно', 'porn', '');
  const c = (await store.getState()).categories[0];
  assert.equal(c.enabled, false);
  assert.equal(c.bundledList, 'adult');
  assert.deepEqual(c.keywords, ['porn']);
});

test('incrementStat counts per category', async () => {
  const store = createStore(fakeArea());
  const other = await store.saveCategory(null, 'Б', 'b', '');
  await store.incrementStat('porn');
  await store.incrementStat('porn');
  await store.incrementStat(other);
  assert.deepEqual((await store.getState()).stats, { porn: 2, [other]: 1 });
});

test('deleteCategory removes its stats', async () => {
  const store = createStore(fakeArea());
  await store.incrementStat('porn');
  await store.deleteCategory('porn');
  const state = await store.getState();
  assert.deepEqual(state.categories.map((c) => c.id), ['youtube-fun']);
  assert.deepEqual(state.stats, {});
});

test('existing install gains youtube category disabled', async () => {
  const mine = { id: 'porn', name: 'Порно', enabled: true, keywords: ['мое'], domains: [], bundledList: 'adult' };
  const area = fakeArea({ categories: [mine], stats: { porn: 5 } });
  const state = await createStore(area).getState();
  assert.deepEqual(state.categories.map((c) => [c.id, c.enabled]), [['porn', true], ['youtube-fun', false]]);
  assert.deepEqual(state.categories[0].keywords, ['мое']);
  assert.deepEqual(state.stats, { porn: 5 });
});

test('a deleted built-in category is not added back', async () => {
  const store = createStore(fakeArea());
  await store.getState();
  await store.deleteCategory('youtube-fun');
  assert.deepEqual((await store.getState()).categories.map((c) => c.id), ['porn']);
});

import { setPauseMode, initialLockState, startCountdown, settle } from '../extension/src/core/lock.js';

async function lockedStore() {
  const store = createStore(fakeArea());
  await store.getState();
  await store.saveLock(setPauseMode(initialLockState(), 20));
  return store;
}

test('lock defaults to unconfigured and is saved', async () => {
  const store = createStore(fakeArea());
  assert.equal((await store.getLock()).mode, null);
  await store.saveLock(setPauseMode(initialLockState(), 15));
  assert.equal((await store.getLock()).pauseMinutes, 15);
});

test('disabling a category without a window is rejected', async () => {
  const store = await lockedStore();
  await assert.rejects(store.setEnabled('porn', false, 0), /locked/);
  assert.equal((await store.getState()).categories[0].enabled, true);
});

test('adding a word and enabling are allowed while locked', async () => {
  const store = await lockedStore();
  const porn = (await store.getState()).categories[0];
  await store.saveCategory('porn', 'Порно', [...porn.keywords, 'новое'].join('\n'), '', null, null, 0);
  await store.setEnabled('youtube-fun', true, 0);
  const s = await store.getState();
  assert.ok(s.categories[0].keywords.includes('новое'));
  assert.equal(s.categories[1].enabled, true);
});

test('removing a word or deleting is rejected while locked', async () => {
  const store = await lockedStore();
  await assert.rejects(store.saveCategory('porn', 'Порно', '', '', null, null, 0), /locked/);
  await assert.rejects(store.deleteCategory('porn', 0), /locked/);
});

test('deleting is allowed inside the window', async () => {
  const store = await lockedStore();
  await store.saveLock(settle(startCountdown(await store.getLock(), 0), 20 * 60_000));
  await store.deleteCategory('porn', 21 * 60_000);
  assert.deepEqual((await store.getState()).categories.map((c) => c.id), ['youtube-fun']);
});

test('saveCategory sets url prefixes and only-hosts when given', async () => {
  const store = createStore(fakeArea());
  const id = await store.saveCategory(null, 'VK', '', '', 'https://www.vk.com/clips', 'vk.com');
  const c = (await store.getState()).categories.find((x) => x.id === id);
  assert.deepEqual([c.urlPrefixes, c.onlyHosts], [['vk.com/clips'], ['vk.com']]);
});
