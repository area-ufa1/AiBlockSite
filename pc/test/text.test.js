import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normalize, tokenize, extractHost } from '../extension/src/core/text.js';

test('normalize lowercases and folds yo', () => assert.equal(normalize('  ЁЖИК '), 'ежик'));

test('tokenize splits on non letters', () =>
  assert.deepEqual(tokenize('Free-PORN | Видео!'), ['free', 'porn', 'видео']));

test('extractHost handles missing scheme and www', () => {
  assert.equal(extractHost('www.pornhub.com/view?x=1'), 'pornhub.com');
  assert.equal(extractHost('https://m.example.org'), 'm.example.org');
  assert.equal(extractHost('поиск в google'), null);
});

test('extractHost strips port and userinfo', () =>
  assert.equal(extractHost('http://user@Example.com:8080/a'), 'example.com'));
