import { test } from 'node:test';
import assert from 'node:assert/strict';
import { decide } from '../extension/src/core/decide.js';
import { DEFAULT_CATEGORIES } from '../extension/src/core/categories.js';

test('non-http urls are never blocked', () => {
  for (const url of ['chrome://extensions', 'browser://tune', 'chrome-extension://abc/options/options.html', 'file:///C:/porn.html'])
    assert.equal(decide({ url, title: 'porn' }, DEFAULT_CATEGORIES, new Map()), null);
});

test('allowlisted claude page is never blocked', () =>
  assert.equal(decide({ url: 'https://claude.ai/new?q=porn', title: 'porn' }, DEFAULT_CATEGORIES, new Map()), null));

test('decide returns redirect to claude', () => {
  const d = decide({ url: 'https://x.com/porn', title: null }, DEFAULT_CATEGORIES, new Map());
  assert.equal(d.categoryId, 'porn');
  assert.equal(d.categoryName, 'Порно');
  assert.ok(d.redirectUrl.startsWith('https://claude.ai/new?q='));
  assert.ok(d.prompt.includes('«Порно»'));
});

test('ordinary page is not blocked', () =>
  assert.equal(decide({ url: 'https://news.example.org/', title: 'Новости' }, DEFAULT_CATEGORIES, new Map()), null));

test('percent-encoded cyrillic search query is blocked before load', () => {
  const url = new URL('https://www.google.com/search?q=порно').href;
  assert.ok(url.includes('%D0%BF'));
  assert.equal(decide({ url, title: null }, DEFAULT_CATEGORIES, new Map())?.categoryId, 'porn');
  const yandex = new URL('https://yandex.ru/search/?text=секс+видео').href;
  assert.equal(decide({ url: yandex, title: null }, DEFAULT_CATEGORIES, new Map())?.categoryId, 'porn');
});

test('prompt shows the decoded address to Claude', () => {
  const url = new URL('https://www.google.com/search?q=порно').href;
  const d = decide({ url, title: null }, DEFAULT_CATEGORIES, new Map());
  assert.ok(d.prompt.includes('q=порно'), d.prompt);
});

test('malformed percent-encoding does not throw', () =>
  assert.equal(decide({ url: 'https://x.com/%E0%A4%A', title: null }, DEFAULT_CATEGORIES, new Map()), null));
