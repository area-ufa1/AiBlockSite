import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildSupportPrompt, chatgptUrl } from '../extension/src/core/prompt.js';

test('ChatGPT url encodes spaces as %20', () => assert.equal(chatgptUrl('а b'), 'https://chatgpt.com/?q=%D0%B0%20b'));

test('support prompt names category and title', () => {
  const p = buildSupportPrompt({ url: 'x.com', title: 'Видео' }, 'Порно');
  assert.ok(p.startsWith('Я только что чуть не открыл страницу из категории «Порно»: «Видео».'));
  assert.ok(p.includes('помоги вернуться к делу'));
});

test('support prompt falls back to url when title blank', () =>
  assert.ok(buildSupportPrompt({ url: 'x.com/a', title: '  ' }, 'Порно').includes('«x.com/a»')));

test('YouTube prompt includes title and URL for analysis', () => {
  const p = buildSupportPrompt({ url: 'https://youtube.com/watch?v=abc', title: 'Полезное видео' }, 'YouTube: развлечения', 'youtube');
  assert.ok(p.includes('Полезное видео'));
  assert.ok(p.includes('https://youtube.com/watch?v=abc'));
  assert.ok(p.includes('Проанализируй этот ролик'));
});

test('support prompt truncates page to 200 chars', () =>
  assert.ok(!buildSupportPrompt({ url: null, title: 'я'.repeat(500) }, 'Порно').includes('я'.repeat(201))));

test('support prompt includes bounded Kanban focus context and three easy actions', () => {
  const p = buildSupportPrompt({ url: 'https://example.com', title: 'Отвлекающая страница' }, 'Порно', 'adult', {
    kanbanContext: 'Доска Kanban: Учёба\n1. Прочитать один абзац',
  });
  assert.match(p, /Контекст моего Kanban/);
  assert.match(p, /ровно три конкретных лёгких действия/);
  assert.match(p, /Прочитать один абзац/);
});
