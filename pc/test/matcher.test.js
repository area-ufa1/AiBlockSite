import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isAllowlisted, matchTab } from '../extension/src/core/matcher.js';

const porn = { id: '1', name: 'Порно', enabled: true, keywords: ['порно', 'porn', 'sex', 'only fans'], domains: ['pornhub.com'], bundledList: null };
const none = new Map();
const tab = (url, title) => ({ url, title });

test('domain match includes subdomains', () =>
  assert.deepEqual(matchTab(tab('rt.pornhub.com/v', 'Video'), [porn], none),
    { categoryId: '1', categoryName: 'Порно', source: 'domain' }));

test('domain does not match lookalike suffix', () =>
  assert.equal(matchTab(tab('notpornhub.com', 'Video'), [{ ...porn, keywords: [] }], none), null));

test('keyword matches inflected russian', () =>
  assert.equal(matchTab(tab('x.ru', 'Смотреть порнографию'), [porn], none)?.source, 'keyword'));

test('keyword matches in url', () =>
  assert.notEqual(matchTab(tab('google.com/search?q=free+porn', 'Google'), [porn], none), null));

test('search query in url matches keyword', () =>
  assert.equal(matchTab(tab('https://www.google.com/search?q=free+porn', null), [porn], none)?.source, 'keyword'));

test('keyword matches only at token start', () =>
  assert.equal(matchTab(tab('bbc.co.uk', 'Sussex and Essex news'), [porn], none), null));

test('multi word keyword', () =>
  assert.notEqual(matchTab(tab(null, 'Only Fans leaks'), [porn], none), null));

test('multi word keyword needs adjacent tokens', () =>
  assert.equal(matchTab(tab(null, 'Only the fans'), [{ ...porn, keywords: ['only fans'] }], none), null));

test('blank keywords are ignored', () =>
  assert.equal(matchTab(tab('news.ru', 'Новости'), [{ ...porn, keywords: ['', '   ', '-'], domains: ['', ' '] }], none), null));

test('disabled category never matches', () =>
  assert.equal(matchTab(tab('pornhub.com', 'porn'), [{ ...porn, enabled: false }], none), null));

test('domains are checked before keywords across categories', () => {
  const words = { id: '2', name: 'Слова', enabled: true, keywords: ['video'], domains: [], bundledList: null };
  assert.equal(matchTab(tab('pornhub.com', 'video'), [words, porn], none)?.source, 'domain');
});

test('bundled list domains match', () => {
  const cat = { id: 'p', name: 'Порно', enabled: true, keywords: [], domains: [], bundledList: 'adult' };
  const bundled = new Map([['adult', new Set(['pornhub.com'])]]);
  assert.deepEqual(matchTab(tab('https://rt.pornhub.com/v', null), [cat], bundled),
    { categoryId: 'p', categoryName: 'Порно', source: 'domain' });
});

test('allowlisted hosts are never blocked', () => {
  assert.equal(isAllowlisted('claude.ai/new?q=порно'), true);
  assert.equal(isAllowlisted('https://www.anthropic.com/x'), true);
  assert.equal(isAllowlisted('https://notclaude.ai.evil.com'), false);
  assert.equal(isAllowlisted(null), false);
});
