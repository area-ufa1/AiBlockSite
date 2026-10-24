import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { decide } from '../extension/src/core/decide.js';
import { matchTab } from '../extension/src/core/matcher.js';
import { DEFAULT_CATEGORIES } from '../extension/src/core/categories.js';
import { parseKeywordList } from '../extension/src/bundled.js';

const read = (f) => parseKeywordList(readFileSync(new URL(`../../app/src/main/assets/blocklists/${f}`, import.meta.url), 'utf8'));
const bundledKeywords = new Map([['adult', read('adult-keywords.txt')], ['youtube-fun', read('youtube-fun-keywords.txt')]]);
const enabled = DEFAULT_CATEGORIES.map((c) => ({ ...c, enabled: true }));
const check = (url, title = null, cats = enabled) => decide({ url, title }, cats, new Map(), bundledKeywords);
const video = (title) => check('https://www.youtube.com/watch?v=abc', `${title} - YouTube`);

test('youtube category is disabled by default', () => {
  const yt = DEFAULT_CATEGORIES.find((c) => c.id === 'youtube-fun');
  assert.equal(yt.name, 'YouTube: развлечения');
  assert.equal(yt.enabled, false);
  assert.deepEqual(yt.urlPrefixes, ['youtube.com/shorts/']);
  assert.deepEqual(yt.onlyHosts, ['youtube.com', 'youtu.be']);
  assert.equal(check('https://www.youtube.com/shorts/abc', null, DEFAULT_CATEGORIES), null);
});

test('shorts url is blocked before load', () => {
  const m = matchTab({ url: 'https://www.youtube.com/shorts/abc', title: null }, enabled, new Map(), bundledKeywords);
  assert.equal(m.categoryId, 'youtube-fun');
  assert.equal(m.source, 'prefix');
});

test('mobile youtube shorts too', () => assert.equal(check('https://m.youtube.com/shorts/x')?.categoryId, 'youtube-fun'));

test('entertainment titles on youtube are blocked', () => {
  for (const t of ['Лучшие приколы 2026', 'Реакция на мемы', 'Funny fails compilation', 'Пранк над другом', 'Стрим по майнкрафту', 'Try not to laugh', 'Reaction to the new trailer'])
    assert.equal(video(t)?.categoryId, 'youtube-fun', t);
});

test('educational titles pass', () => {
  for (const t of ['Лекция по физике', 'Урок английского для начинающих', 'Как починить кран', 'Documentary: Ocean life', 'Разбор задачи ЕГЭ', 'Python tutorial for beginners', 'Химические реакции', 'Coding challenge for beginners', 'Мемориал Победы', 'Топ 10 фактов о космосе'])
    assert.equal(video(t), null, t);
});

test('youtube words apply only on youtube', () => {
  assert.equal(check('https://news.ru/a', 'Реакция рынка на ставку ЦБ'), null);
  assert.equal(check('https://habr.com/x', 'Мемы про программистов'), null);
});

test('youtube home and search are not blocked', () => {
  assert.equal(check('https://www.youtube.com/', 'YouTube'), null);
  assert.equal(check('https://www.youtube.com/results?search_query=физика', 'физика - YouTube'), null);
});

test('youtube block uses its own prompt', () => {
  const d = check('https://www.youtube.com/shorts/abc');
  assert.ok(d.prompt.includes('развлекательный YouTube'), d.prompt);
  const p = check('https://x.com/porn');
  assert.ok(p.prompt.includes('«Порно»'));
});

test('custom url prefix in any category blocks', () => {
  const c = { id: 'c', name: 'Свои', enabled: true, keywords: [], domains: [], bundledList: null, urlPrefixes: ['vk.com/clips'], onlyHosts: [] };
  assert.equal(check('https://vk.com/clips/123', null, [c])?.categoryId, 'c');
  assert.equal(check('https://vk.com/feed', null, [c]), null);
});
