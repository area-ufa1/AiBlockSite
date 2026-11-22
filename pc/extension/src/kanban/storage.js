export const KANBAN_BASE_URL = 'https://kanban.unit-urfu.ru';

const DEFAULT_SETTINGS = Object.freeze({
  enabled: false,
  baseUrl: KANBAN_BASE_URL,
  token: '',
});

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
    baseUrl: KANBAN_BASE_URL,
    token: String(settings.token ?? '').trim(),
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
