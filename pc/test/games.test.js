import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DEFAULT_CATEGORIES } from '../extension/src/core/categories.js';
import { matchTab } from '../extension/src/core/matcher.js';
import { parseKeywordList } from '../extension/src/bundled.js';

const games = DEFAULT_CATEGORIES.find((category) => category.id === 'games');
const domains = new Set(parseKeywordList(readFileSync(new URL('../blocklists/games.txt', import.meta.url), 'utf8')));

test('games category blocks Steam, Roblox and BlueStacks', () => {
  for (const host of ['store.steampowered.com', 'www.roblox.com', 'www.bluestacks.com']) {
    const match = matchTab({ url: `https://${host}/`, title: host }, [games], new Map([['games', domains]]));
    assert.equal(match?.categoryId, 'games', host);
  }
});

test('games category leaves ordinary sites alone', () => {
  assert.equal(matchTab({ url: 'https://example.org/', title: 'Course' }, [games], new Map([['games', domains]])), null);
});
