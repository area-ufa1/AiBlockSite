import test from 'node:test';
import assert from 'node:assert/strict';
import { KANBAN_BASE_URL, createKanbanStorage, normalizeKanbanSettings, normalizeKanbanUrl } from '../extension/src/kanban/storage.js';
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

test('normalizes Kanban settings to the production service and rejects non-http URLs', () => {
  assert.equal(normalizeKanbanUrl('http://localhost:8000/'), 'http://localhost:8000');
  assert.throws(() => normalizeKanbanUrl('file:///tmp/kanban'), /http or https/);
  assert.deepEqual(normalizeKanbanSettings({ baseUrl: 'http://localhost:8000', token: 'tg_secret', boardId: 'b1' }), {
    enabled: false,
    baseUrl: KANBAN_BASE_URL,
    token: 'tg_secret',
  });
});

test('stores Kanban settings in one local storage record', async () => {
  const storage = createKanbanStorage(memoryArea());
  await storage.save({ enabled: true, token: 'tg_secret' });
  assert.deepEqual(await storage.get(), {
    enabled: true,
    baseUrl: KANBAN_BASE_URL,
    token: 'tg_secret',
  });
});

test('fetches bounded context with scoped authorization and no credentials', async () => {
  let request;
  const result = await fetchKanbanContext({ token: 'tg_secret' }, async (url, init) => {
    request = { url: String(url), init };
    return new Response(JSON.stringify({ workspace_id: 7, board: { id: 'b1' }, column: { id: 'c1' }, cards: [] }), { status: 200, headers: { 'content-type': 'application/json' } });
  });
  assert.deepEqual(result.cards, []);
  assert.equal(request.url, `${KANBAN_BASE_URL}/api/integrations/tabguard/context`);
  assert.equal(request.init.credentials, 'omit');
  assert.equal(request.init.headers.Authorization, 'TabGuard tg_secret');
});

test('surfaces API error code without including the token', async () => {
  await assert.rejects(
    fetchKanbanContext({ token: 'tg_secret' }, async () => new Response(JSON.stringify({ code: 'TOKEN_REVOKED', detail: 'revoked' }), { status: 401 })),
    (error) => error.status === 401 && error.code === 'TOKEN_REVOKED' && !error.message.includes('tg_secret'),
  );
});

test('creates a Kanban focus session with the integration token', async () => {
  const requests = [];
  const { createKanbanFocusSession } = await import('../extension/src/kanban/client.js');
  const response = await createKanbanFocusSession({ token: 'tg_secret' }, { title: 'Read one page', description: 'Start with the first paragraph', sourceUrl: 'https://chatgpt.com/' }, async (url, init) => {
    requests.push({ url: String(url), init });
    if (requests.length === 1) return new Response(JSON.stringify({ workspace_id: 4, board: { id: 5 }, column: { id: 6 }, cards: [] }), { status: 200 });
    return new Response(JSON.stringify({ action: 'created', card_id: 12 }), { status: 200, headers: { 'content-type': 'application/json' } });
  });
  assert.equal(response.card_id, 12);
  assert.equal(requests[1].init.headers.Authorization, 'TabGuard tg_secret');
  assert.equal(JSON.parse(requests[1].init.body).workspace_id, 4);
  assert.equal(JSON.parse(requests[1].init.body).source, 'tabguard_chatgpt');
});

test('moves an existing Kanban card with its optimistic-lock version', async () => {
  let request;
  const { moveKanbanCard } = await import('../extension/src/kanban/client.js');
  await moveKanbanCard({ token: 'tg_secret' }, 12, 3, async (url, init) => {
    request = { url: String(url), init };
    if (String(url).endsWith('/context')) return new Response(JSON.stringify({ workspace_id: 4, board: { id: 5 }, column: { id: 6 }, cards: [] }), { status: 200 });
    return new Response(JSON.stringify({ action: 'moved', card_id: 12 }), { status: 200 });
  });
  const body = JSON.parse(request.init.body);
  assert.equal(body.existing_card_id, 12);
  assert.equal(body.expected_version, 3);
});

test('normalizes context to bounded prompt-safe fields', () => {
  const context = normalizeKanbanContext({
    workspace_id: 4,
    board: { id: 1, title: 'Focus' },
    column: { id: 2, title: 'Next' },
    cards: [{ id: 3, title: '  Start  ', description: 'x'.repeat(900), priority: 'high', deadline: '2026-11-23T10:00:00Z', column_id: 2, version: 4 }, ...Array.from({ length: 20 }, (_, id) => ({ id: id + 10, title: `Card ${id}`, version: 1 }))],
  });
  assert.equal(context.cards.length, 10);
  assert.equal(context.cards[0].description.length, 600);
  assert.equal(context.cards[0].version, 4);
  assert.match(kanbanContextForPrompt(context), /Доска Kanban: Focus/);
  assert.match(kanbanContextForPrompt(context), /1\. Start/);
});
