import { normalizeKanbanSettings } from './storage.js';
import { normalizeKanbanContext } from './context.js';

function makeUrl(baseUrl, path, params = {}) {
  const url = new URL(path, `${baseUrl}/`);
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value);
  });
  return url;
}

async function fetchWithTimeout(fetchImpl, url, init, timeoutMs = 5000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetchImpl(url, { ...init, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
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
  if (!normalized.token) {
    throw new Error('Введите Kanban токен');
  }
  const url = makeUrl(normalized.baseUrl, '/api/integrations/tabguard/context');
  const response = await fetchWithTimeout(fetchImpl, url, {
    headers: { Authorization: `TabGuard ${normalized.token}` },
    credentials: 'omit',
  });
  return parseResponse(response);
}

export async function createKanbanFocusSession(settings, action, fetchImpl = fetch) {
  const normalized = normalizeKanbanSettings(settings);
  if (!normalized.token) throw new Error('Введите Kanban токен');
  const context = normalizeKanbanContext(await fetchKanbanContext(normalized, fetchImpl));
  if (!context) throw new Error('Kanban вернул неполный контекст');
  const ids = [context.workspaceId, context.board.id, context.column.id].map(Number);
  if (ids.some((value) => !Number.isInteger(value) || value < 1)) throw new Error('Kanban IDs must be positive integers');
  const title = String(action?.title ?? '').trim().slice(0, 255);
  const description = String(action?.description ?? '').trim().slice(0, 4000);
  if (!title) throw new Error('The suggested task has no title');
  const body = {
    client_request_id: action?.clientRequestId || crypto.randomUUID(),
    workspace_id: ids[0],
    board_id: ids[1],
    column_id: ids[2],
    title,
    description,
    source: 'tabguard_chatgpt',
    source_url: String(action?.sourceUrl ?? '').slice(0, 500),
  };
  if (action?.existingCardId !== undefined && action?.existingCardId !== null) {
    body.existing_card_id = Number(action.existingCardId);
    body.expected_version = Number(action.expectedVersion);
  }
  const response = await fetchWithTimeout(fetchImpl, makeUrl(normalized.baseUrl, '/api/integrations/tabguard/focus-sessions'), {
    method: 'POST',
    headers: {
      Authorization: `TabGuard ${normalized.token}`,
      'Content-Type': 'application/json',
    },
    credentials: 'omit',
    body: JSON.stringify(body),
  });
  return parseResponse(response);
}

export async function moveKanbanCard(settings, cardId, expectedVersion, fetchImpl = fetch) {
  return createKanbanFocusSession(settings, {
    title: 'Перемещение карточки',
    existingCardId: cardId,
    expectedVersion,
    description: '',
  }, fetchImpl);
}

export function createKanbanClient(settings, fetchImpl = fetch) {
  return {
    getContext: () => fetchKanbanContext(settings, fetchImpl),
    createFocusSession: (action) => createKanbanFocusSession(settings, action, fetchImpl),
    moveCard: (cardId, expectedVersion) => moveKanbanCard(settings, cardId, expectedVersion, fetchImpl),
  };
}
