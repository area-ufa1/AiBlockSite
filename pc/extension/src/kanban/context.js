const MAX_CARDS = 10;
const MAX_TITLE = 200;
const MAX_DESCRIPTION = 600;

function text(value, max) {
  return String(value ?? '').trim().slice(0, max);
}

export function normalizeKanbanContext(payload) {
  if (!payload || typeof payload !== 'object') return null;
  const board = payload.board && {
    id: payload.board.id,
    title: text(payload.board.title, MAX_TITLE),
  };
  const column = payload.column && {
    id: payload.column.id,
    title: text(payload.column.title, MAX_TITLE),
  };
  if (!board || !column || !Array.isArray(payload.cards)) return null;
  const cards = payload.cards.slice(0, MAX_CARDS).map((card) => ({
    id: card?.id,
    title: text(card?.title, MAX_TITLE),
    description: text(card?.description, MAX_DESCRIPTION),
    priority: text(card?.priority, 32),
    deadline: text(card?.deadline, 64),
    columnId: card?.column_id,
    version: card?.version,
  })).filter((card) => card.id !== undefined && card.title);
  return { board, column, cards };
}

export function kanbanContextForPrompt(context) {
  const normalized = normalizeKanbanContext(context);
  if (!normalized) return '';
  const lines = [
    `Доска Kanban: ${normalized.board.title || `#${normalized.board.id}`}`,
    `Колонка для фокуса: ${normalized.column.title || `#${normalized.column.id}`}`,
  ];
  if (normalized.cards.length === 0) {
    lines.push('В выбранной колонке пока нет карточек.');
  } else {
    lines.push('Карточки в этой колонке:');
    normalized.cards.forEach((card, index) => {
      const details = [card.description, card.priority && `приоритет: ${card.priority}`, card.deadline && `срок: ${card.deadline}`].filter(Boolean);
      lines.push(`${index + 1}. ${card.title}${details.length ? ` — ${details.join('; ')}` : ''}`);
    });
  }
  return lines.join('\n');
}

export { MAX_CARDS, MAX_TITLE, MAX_DESCRIPTION };
