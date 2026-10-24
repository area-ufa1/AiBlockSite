import { test } from 'node:test';
import assert from 'node:assert/strict';
import { decide } from '../extension/src/core/decide.js';
import { DEFAULT_CATEGORIES } from '../extension/src/core/categories.js';
import { isEntertainmentGenre } from '../extension/src/core/youtube.js';

const withYoutube = (enabled) => DEFAULT_CATEGORIES.map((c) => (c.id === 'youtube-fun' ? { ...c, enabled } : c));
const watch = (genre, title = 'Я купил остров - YouTube', url = 'https://www.youtube.com/watch?v=abc') =>
  decide({ url, title, genre }, withYoutube(true), new Map(), new Map());

test('entertainment genres are recognized', () => {
  for (const g of ['Entertainment', 'Comedy', 'Gaming', 'People & Blogs', 'Film & Animation', 'Pets & Animals', 'Music'])
    assert.ok(isEntertainmentGenre(g), g);
  for (const g of ['Education', 'Science & Technology', 'Howto & Style', 'News & Politics', 'Sports', 'Autos & Vehicles',
    'Nonprofits & Activism', 'Travel & Events', '', null, undefined])
    assert.ok(!isEntertainmentGenre(g), String(g));
});

test('video with an entertainment genre is blocked even with a neutral title', () => {
  const d = watch('Entertainment');
  assert.equal(d?.categoryId, 'youtube-fun');
  assert.ok(d.prompt.includes('развлекательный YouTube'), d.prompt);
});

test('educational genre passes', () => assert.equal(watch('Education'), null));

test('genre is ignored when the youtube category is off', () =>
  assert.equal(decide({ url: 'https://www.youtube.com/watch?v=abc', title: 'x', genre: 'Gaming' }, withYoutube(false), new Map(), new Map()), null));

test('genre is ignored outside youtube', () =>
  assert.equal(watch('Gaming', 'Something', 'https://vimeo.com/123'), null));
