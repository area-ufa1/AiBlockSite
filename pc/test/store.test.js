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
  assert.deepEqual(state.categories, [mine]);
  assert.deepEqual(state.stats, { a: 3 });
});

test('saveCategory creates a new enabled category with parsed input', async () => {
  const store = createStore(fakeArea());
  const id = await store.saveCategory(null, '  Казино ', 'Casino,\n\nСтавки', 'https://www.Bet.com/x');
  const c = (await store.getState()).categories.find((x) => x.id === id);
  assert.deepEqual({ ...c, id: undefined }, { id: undefined, name: 'Казино', enabled: true, keywords: ['casino', 'ставки'], domains: ['bet.com'], bundledList: null });
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
  assert.deepEqual(state.categories, []);
  assert.deepEqual(state.stats, {});
});
