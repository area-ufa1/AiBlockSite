import { test } from 'node:test';
import assert from 'node:assert/strict';
import { safeSearchUrl } from '../extension/src/core/safesearch.js';

test('google search gets safe=active', () => {
  assert.equal(safeSearchUrl('https://www.google.com/search?q=cats'), 'https://www.google.com/search?q=cats&safe=active');
  assert.equal(safeSearchUrl('https://www.google.ru/search?q=x&tbm=isch'), 'https://www.google.ru/search?q=x&tbm=isch&safe=active');
  assert.equal(safeSearchUrl('https://www.google.com.ua/search?q=x&safe=off'), 'https://www.google.com.ua/search?q=x&safe=active');
});

test('yandex search gets family=yes', () => {
  assert.equal(safeSearchUrl('https://yandex.ru/search/?text=x'), 'https://yandex.ru/search/?text=x&family=yes');
  assert.equal(safeSearchUrl('https://yandex.ru/images/search?text=x'), 'https://yandex.ru/images/search?text=x&family=yes');
  assert.equal(safeSearchUrl('https://ya.ru/search/?text=x'), 'https://ya.ru/search/?text=x&family=yes');
});

test('bing and duckduckgo get strict mode', () => {
  assert.equal(safeSearchUrl('https://www.bing.com/search?q=x'), 'https://www.bing.com/search?q=x&adlt=strict');
  assert.equal(safeSearchUrl('https://www.bing.com/images/search?q=x'), 'https://www.bing.com/images/search?q=x&adlt=strict');
  assert.equal(safeSearchUrl('https://duckduckgo.com/?q=x'), 'https://duckduckgo.com/?q=x&kp=1');
});

test('already safe or not a search returns null', () => {
  for (const url of [
    'https://www.google.com/search?q=x&safe=active',
    'https://yandex.ru/search/?text=x&family=yes',
    'https://www.bing.com/search?q=x&adlt=strict',
    'https://duckduckgo.com/?q=x&kp=1',
    'https://www.google.com/',
    'https://mail.google.com/mail/u/0/',
    'https://www.google.com/maps/search/cafe',
    'https://yandex.ru/maps/',
    'https://music.yandex.ru/search?text=x',
    'https://duckduckgo.com/about',
    'https://example.com/search?q=x',
    'chrome://extensions',
    'not a url',
  ]) assert.equal(safeSearchUrl(url), null, url);
});
