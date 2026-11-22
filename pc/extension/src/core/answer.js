const MAX_ACTIONS = 3;
const MAX_TITLE = 200;
const MAX_DESCRIPTION = 600;

function clean(value, max) {
  return String(value ?? '').replace(/\s+/g, ' ').trim().slice(0, max);
}

export function parseFocusActions(answer) {
  const text = String(answer ?? '').trim();
  if (!text) return [];
  const marker = text.match(/<tabguard-focus-options>\s*([\s\S]*?)\s*<\/tabguard-focus-options>/i);
  if (marker) {
    try {
      const parsed = JSON.parse(marker[1]);
      const marked = Array.isArray(parsed.options) ? parsed.options : [];
      const actions = marked.slice(0, MAX_ACTIONS).map((item) => ({
        title: clean(item?.title, MAX_TITLE),
        description: clean(item?.description || item?.title, MAX_DESCRIPTION),
      })).filter((action) => action.title.length >= 3);
      if (actions.length) return actions;
    } catch {
      // Fall back to the human-readable numbered list.
    }
  }
  const lines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  const numbered = lines.filter((line) => /^(?:\d+|[•*-])[.)]?\s+/.test(line));
  const candidates = (numbered.length ? numbered : lines).map((line) => line.replace(/^(?:\d+|[•*-])[.)]?\s+/, ''));
  return candidates.slice(0, MAX_ACTIONS).map((line) => {
    const title = clean(line.replace(/^\*+|\*+$/g, ''), MAX_TITLE);
    return { title, description: title };
  }).filter((action) => action.title.length >= 3);
}

export function buildFocusCardDescription(action, answer, sourceUrl) {
  const lines = [
    `Предложено ChatGPT: ${clean(action?.description || action?.title, MAX_DESCRIPTION)}`,
    sourceUrl ? `Источник: ${String(sourceUrl).slice(0, 500)}` : '',
    answer ? `Ответ ChatGPT: ${clean(answer, MAX_DESCRIPTION)}` : '',
  ].filter(Boolean);
  return lines.join('\n').slice(0, MAX_DESCRIPTION);
}

export { MAX_ACTIONS, MAX_TITLE, MAX_DESCRIPTION };
