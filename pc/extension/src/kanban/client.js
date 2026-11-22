import { normalizeKanbanSettings } from './storage.js';

function makeUrl(baseUrl, path, params = {}) {
  const url = new URL(path, `${baseUrl}/`);
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value);
  });
  return url;
}

async function parseResponse(response) {
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(payload.detail || payload.message || `Kanban request failed (${response.status})`);
    error.status = response.status;
    error.code = payload.code;
    throw error;
  }
  return payload;
}

export async function fetchKanbanContext(settings, fetchImpl = fetch) {
  const normalized = normalizeKanbanSettings(settings);
  if (!normalized.baseUrl || !normalized.token || !normalized.boardId || !normalized.columnId) {
    throw new Error('Fill in the Kanban URL, token, board ID and column ID');
  }
  const url = makeUrl(normalized.baseUrl, '/api/integrations/tabguard/context', {
    board_id: normalized.boardId,
    column_id: normalized.columnId,
    limit: normalized.limit,
  });
  const response = await fetchImpl(url, {
    headers: { Authorization: `TabGuard ${normalized.token}` },
    credentials: 'omit',
  });
  return parseResponse(response);
}

export async function createKanbanFocusSession(settings, action, fetchImpl = fetch) {
  const normalized = normalizeKanbanSettings(settings);
  if (!normalized.baseUrl || !normalized.token || !normalized.workspaceId || !normalized.boardId || !normalized.columnId) {
    throw new Error('Fill in the Kanban URL, token, workspace ID, board ID and column ID');
  }
  const ids = ['workspaceId', 'boardId', 'columnId'].map((field) => Number(normalized[field]));
  if (ids.some((value) => !Number.isInteger(value) || value < 1)) throw new Error('Kanban IDs must be positive integers');
  const title = String(action?.title ?? '').trim().slice(0, 255);
  const description = String(action?.description ?? '').trim().slice(0, 4000);
  if (!title) throw new Error('The suggested task has no title');
  const response = await fetchImpl(makeUrl(normalized.baseUrl, '/api/integrations/tabguard/focus-sessions'), {
    method: 'POST',
    headers: {
      Authorization: `TabGuard ${normalized.token}`,
      'Content-Type': 'application/json',
    },
    credentials: 'omit',
    body: JSON.stringify({
      client_request_id: crypto.randomUUID(),
      workspace_id: ids[0],
      board_id: ids[1],
      column_id: ids[2],
      title,
      description,
      source: 'tabguard_chatgpt',
      source_url: String(action?.sourceUrl ?? '').slice(0, 500),
    }),
  });
  return parseResponse(response);
}

export function createKanbanClient(settings, fetchImpl = fetch) {
  return {
    getContext: () => fetchKanbanContext(settings, fetchImpl),
    createFocusSession: (action) => createKanbanFocusSession(settings, action, fetchImpl),
  };
}
