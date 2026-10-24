import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DEFAULT_CATEGORIES, parseKeywords, parseDomains } from '../extension/src/core/categories.js';

test('default category is porn with bundled list', () => {
  assert.deepEqual(DEFAULT_CATEGORIES.map((c) => c.id), ['porn', 'youtube-fun']);
  const c = DEFAULT_CATEGORIES[0];
  assert.deepEqual([c.id, c.name, c.enabled, c.bundledList], ['porn', 'Порно', true, 'adult']);
  assert.deepEqual(c.keywords, ['порно', 'porn', 'xxx', 'секс', 'sex', 'хентай', 'hentai', 'nsfw', 'onlyfans', 'эротика']);
});

test('parseKeywords drops blanks and duplicates', () =>
  assert.deepEqual(parseKeywords('Порно,\n\n XXX ,xxx'), ['порно', 'xxx']));

test('parseDomains normalizes urls', () =>
  assert.deepEqual(parseDomains('https://www.X.com/\nне домен'), ['x.com']));

test('parseDomains converts cyrillic domains to punycode like the browser reports', () =>
  assert.deepEqual(parseDomains('порно.рф'), [new URL('http://порно.рф').hostname]));
