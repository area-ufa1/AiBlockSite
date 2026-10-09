import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { decide } from '../extension/src/core/decide.js';
import { DEFAULT_CATEGORIES } from '../extension/src/core/categories.js';
import { parseKeywordList } from '../extension/src/bundled.js';

const words = parseKeywordList(readFileSync(new URL('../../app/src/main/assets/blocklists/adult-keywords.txt', import.meta.url), 'utf8'));
const bundledKeywords = new Map([['adult', words]]);
const google = (q) => `https://www.google.com/search?q=${encodeURIComponent(q)}`;
const blocked = (url, title = null) => decide({ url, title }, DEFAULT_CATEGORIES, new Map(), bundledKeywords) !== null;

test('dictionary parses phrases and skips comments', () => {
  assert.ok(words.includes('голые'));
  assert.ok(words.includes('интим фото'));
  assert.ok(!words.some((w) => w.startsWith('#') || w === ''));
});

test('adult searches are blocked', () => {
  for (const q of ['голые', 'голые девушки', 'обнаженная модель', 'Голая актриса', 'golye devushki', 'nude pics',
    'naked women', 'порнуха', 'сиськи', 'интимные фото звезд', 'xvideos', 'эротика фото', 'хентай аниме', 'milf'])
    assert.ok(blocked(google(q)), q);
});

test('ordinary searches are not blocked', () => {
  for (const q of ['забитые голы матча', 'анализ крови', 'cocktail recipes', 'Sussex and Essex', 'assessment test',
    'cumulative frequency', 'член правительства', 'нюансы налогов', 'Голливуд фильмы 2026', 'обнаружение ошибок',
    'трахея болит', 'class action', 'Купить наушники', 'Кинопоиск топ 250', 'analysis of variance', 'Middlesex hospital'])
    assert.ok(!blocked(google(q)), q);
});

test('bundled keywords apply only to enabled categories', () => {
  const off = DEFAULT_CATEGORIES.map((c) => ({ ...c, enabled: false }));
  assert.equal(decide({ url: google('голые'), title: null }, off, new Map(), bundledKeywords), null);
});
