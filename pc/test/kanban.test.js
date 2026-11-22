import test from 'node:test';
import assert from 'node:assert/strict';
import { createKanbanStorage, normalizeKanbanSettings, normalizeKanbanUrl } from '../extension/src/kanban/storage.js';
import { fetchKanbanContext } from '../extension/src/kanban/client.js';
import { kanbanContextForPrompt, normalizeKanbanContext } from '../extension/src/kanban/context.js';

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
    workspaceId: '',
    boardId: '',
    columnId: '',
    limit: 10,
  });
});

test('stores Kanban settings in one local storage record', async () => {
  const storage = createKanbanStorage(memoryArea());
  await storage.save({ enabled: true, baseUrl: 'http://localhost:8000', token: 'tg_secret', workspaceId: 'w1', boardId: 'b1', columnId: 'c1', limit: 3 });
  assert.deepEqual(await storage.get(), {
    enabled: true,
    baseUrl: 'http://localhost:8000',
    token: 'tg_secret',
    workspaceId: 'w1',
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

test('creates a Kanban focus session with the integration token', async () => {
  let request;
  const { createKanbanFocusSession } = await import('../extension/src/kanban/client.js');
  const response = await createKanbanFocusSession({ baseUrl: 'http://localhost:8000', token: 'tg_secret', workspaceId: '4', boardId: '5', columnId: '6' }, { title: 'Read one page', description: 'Start with the first paragraph', sourceUrl: 'https://chatgpt.com/' }, async (url, init) => {
    request = { url: String(url), init };
    return new Response(JSON.stringify({ action: 'created', card_id: 12 }), { status: 200, headers: { 'content-type': 'application/json' } });
  });
  assert.equal(response.card_id, 12);
  assert.equal(request.init.headers.Authorization, 'TabGuard tg_secret');
  assert.equal(JSON.parse(request.init.body).workspace_id, 4);
  assert.equal(JSON.parse(request.init.body).source, 'tabguard_chatgpt');
});

test('normalizes context to bounded prompt-safe fields', () => {
  const context = normalizeKanbanContext({
    board: { id: 1, title: 'Focus' },
    column: { id: 2, title: 'Next' },
    cards: [{ id: 3, title: '  Start  ', description: 'x'.repeat(900), priority: 'high', deadline: '2026-11-23T10:00:00Z', column_id: 2 }, ...Array.from({ length: 20 }, (_, id) => ({ id: id + 10, title: `Card ${id}` }))],
  });
  assert.equal(context.cards.length, 10);
  assert.equal(context.cards[0].description.length, 600);
  assert.match(kanbanContextForPrompt(context), /Доска Kanban: Focus/);
  assert.match(kanbanContextForPrompt(context), /1\. Start/);
});
