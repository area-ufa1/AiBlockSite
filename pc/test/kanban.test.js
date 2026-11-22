import test from 'node:test';
import assert from 'node:assert/strict';
import { createKanbanStorage, normalizeKanbanSettings, normalizeKanbanUrl } from '../extension/src/kanban/storage.js';
import { fetchKanbanContext } from '../extension/src/kanban/client.js';

function memoryArea(initial = {}) {
  const data = { ...initial };
  return {
    async get(key) {
      if (typeof key === 'string') return { [key]: data[key] };
      return Object.fromEntries(key.map((item) => [item, data[item]]));
    },
    async set(values) { Object.assign(data, values); },
  };
}

test('normalizes Kanban settings and rejects non-http URLs', () => {
  assert.equal(normalizeKanbanUrl('http://localhost:8000/'), 'http://localhost:8000');
  assert.throws(() => normalizeKanbanUrl('file:///tmp/kanban'), /http or https/);
  assert.deepEqual(normalizeKanbanSettings({ baseUrl: 'https://kanban.example/app', limit: 99 }), {
    enabled: false,
    baseUrl: 'https://kanban.example',
    token: '',
    boardId: '',
    columnId: '',
    limit: 10,
  });
});

test('stores Kanban settings in one local storage record', async () => {
  const storage = createKanbanStorage(memoryArea());
  await storage.save({ enabled: true, baseUrl: 'http://localhost:8000', token: 'tg_secret', boardId: 'b1', columnId: 'c1', limit: 3 });
  assert.deepEqual(await storage.get(), {
    enabled: true,
    baseUrl: 'http://localhost:8000',
    token: 'tg_secret',
    boardId: 'b1',
    columnId: 'c1',
    limit: 3,
  });
});

test('fetches bounded context with scoped authorization and no credentials', async () => {
  let request;
  const result = await fetchKanbanContext({ baseUrl: 'http://localhost:8000/', token: 'tg_secret', boardId: 'b1', columnId: 'c1', limit: 4 }, async (url, init) => {
    request = { url: String(url), init };
    return new Response(JSON.stringify({ board: { id: 'b1' }, cards: [] }), { status: 200, headers: { 'content-type': 'application/json' } });
  });
  assert.deepEqual(result.cards, []);
  assert.match(request.url, /board_id=b1/);
  assert.match(request.url, /column_id=c1/);
  assert.equal(request.init.credentials, 'omit');
  assert.equal(request.init.headers.Authorization, 'TabGuard tg_secret');
});

test('surfaces API error code without including the token', async () => {
  await assert.rejects(
    fetchKanbanContext({ baseUrl: 'http://localhost:8000', token: 'tg_secret', boardId: 'b1', columnId: 'c1' }, async () => new Response(JSON.stringify({ code: 'TOKEN_REVOKED', detail: 'revoked' }), { status: 401 })),
    (error) => error.status === 401 && error.code === 'TOKEN_REVOKED' && !error.message.includes('tg_secret'),
  );
});
