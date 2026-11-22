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

export function createKanbanClient(settings, fetchImpl = fetch) {
  return {
    getContext: () => fetchKanbanContext(settings, fetchImpl),
  };
}
