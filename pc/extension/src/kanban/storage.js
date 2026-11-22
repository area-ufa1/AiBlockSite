const DEFAULT_SETTINGS = Object.freeze({
  enabled: false,
  baseUrl: '',
  token: '',
  workspaceId: '',
  boardId: '',
  columnId: '',
  limit: 5,
});

function clampLimit(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return DEFAULT_SETTINGS.limit;
  return Math.min(10, Math.max(1, Math.round(number)));
}

export function normalizeKanbanUrl(value) {
  const input = String(value ?? '').trim();
  if (!input) return '';
  const url = new URL(input);
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Kanban URL must use http or https');
  return url.origin;
}

export function normalizeKanbanSettings(value = {}) {
  const settings = { ...DEFAULT_SETTINGS, ...(value ?? {}) };
  return {
    enabled: Boolean(settings.enabled),
    baseUrl: normalizeKanbanUrl(settings.baseUrl),
    token: String(settings.token ?? '').trim(),
    workspaceId: String(settings.workspaceId ?? '').trim(),
    boardId: String(settings.boardId ?? '').trim(),
    columnId: String(settings.columnId ?? '').trim(),
    limit: clampLimit(settings.limit),
  };
}

export function createKanbanStorage(area) {
  async function get() {
    const result = await area.get('tabguardKanban');
    return normalizeKanbanSettings(result.tabguardKanban);
  }

  return {
    get,
    save: async (value) => {
      const next = normalizeKanbanSettings(value);
      await area.set({ tabguardKanban: next });
      return next;
    },
    clear: async () => {
      const next = { ...DEFAULT_SETTINGS };
      await area.set({ tabguardKanban: next });
      return next;
    },
  };
}

export { DEFAULT_SETTINGS };
