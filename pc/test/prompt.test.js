import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildSupportPrompt, claudeUrl } from '../extension/src/core/prompt.js';

test('claude url encodes spaces as %20', () => assert.equal(claudeUrl('а b'), 'https://claude.ai/new?q=%D0%B0%20b'));

test('support prompt names category and title', () => {
  const p = buildSupportPrompt({ url: 'x.com', title: 'Видео' }, 'Порно');
  assert.ok(p.startsWith('Я только что чуть не открыл страницу из категории «Порно»: «Видео».'));
  assert.ok(p.includes('помоги справиться с тягой'));
});

test('support prompt falls back to url when title blank', () =>
  assert.ok(buildSupportPrompt({ url: 'x.com/a', title: '  ' }, 'Порно').includes('«x.com/a»')));

test('support prompt truncates page to 200 chars', () =>
  assert.ok(!buildSupportPrompt({ url: null, title: 'я'.repeat(500) }, 'Порно').includes('я'.repeat(201))));
