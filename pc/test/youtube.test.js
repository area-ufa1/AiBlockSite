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

test('extended youtube dictionary blocks entertainment', () => {
  for (const t of ['Смешные котики 2026', 'Угарные моменты со стрима', 'Мистер Бист: 24 часа в бункере', 'Куплинов играет',
    'Прохождение игры Cyberpunk', 'Майнкрафт выживание', 'Brawl Stars новый боец', 'ASMR для сна', 'Влог из Дубая',
    'Попробуй не засмеяться', 'Что было дальше? Новый выпуск', 'Пусть говорят', 'MrBeast I Survived 100 Days',
    'Fortnite highlights', 'Skibidi toilet 77', 'Day in my life as a student', 'Epic fail moments', 'Тикток подборка'])
    assert.equal(video(t)?.categoryId, 'youtube-fun', t);
});

test('extended youtube dictionary leaves study videos alone', () => {
  for (const t of ['Угарный газ: как не отравиться', 'Как стримить в OBS', 'C++ compilation process explained',
    'Roast chicken recipe', 'Trolley problem explained', 'Sigma notation in math', "Let's play guitar: lesson 1",
    'Дотация регионам', 'Дом 2-х этажный из бруса', 'Горе от ума — комедия Грибоедова', 'Divine Comedy lecture',
    'Hauling a trailer safely', 'Кухня: как сделать ремонт', 'Университет Иннополис', 'Оружие Первой мировой',
    'Химическая реакция горения', 'Мемориал Победы', 'Шоколадный торт рецепт', 'Кровельная жесть монтаж',
    'Урок математики 5 класс', 'Курс Python с нуля', 'Как научиться рисовать скетчи', 'Математический анализ лекция 1'])
    assert.equal(video(t), null, t);
});
