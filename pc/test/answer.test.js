import test from 'node:test';
import assert from 'node:assert/strict';
import { buildFocusCardDescription, parseFocusActions } from '../extension/src/core/answer.js';

test('parses exactly three short actions from a structured answer', () => {
  const actions = parseFocusActions('1. Открыть документ\n2) Прочитать один абзац\n3. Записать один вопрос\n4. Лишний пункт');
  assert.deepEqual(actions.map((item) => item.title), ['Открыть документ', 'Прочитать один абзац', 'Записать один вопрос']);
});

test('falls back to short paragraphs and bounds card description', () => {
  const actions = parseFocusActions('Открыть заметки\nСделать первый маленький шаг');
  assert.equal(actions.length, 2);
  assert.ok(buildFocusCardDescription(actions[0], 'Ответ '.repeat(500), 'https://chatgpt.com/').length <= 600);
});
