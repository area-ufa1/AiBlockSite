import { test } from 'node:test';
import assert from 'node:assert/strict';
import { BlockCooldown } from '../extension/src/core/cooldown.js';

test('same tab and url within 2 s is blocked once', () => {
  let t = 0;
  const c = new BlockCooldown(2000, () => t);
  assert.equal(c.shouldBlock(1, 'u'), true);
  t = 1500;
  assert.equal(c.shouldBlock(1, 'u'), false);
  t = 2500;
  assert.equal(c.shouldBlock(1, 'u'), true);
  assert.equal(c.shouldBlock(2, 'u'), true);
});

test('forget lets a failed block be retried', () => {
  const c = new BlockCooldown(2000, () => 0);
  assert.equal(c.shouldBlock(1, 'u'), true);
  c.forget(1, 'u');
  assert.equal(c.shouldBlock(1, 'u'), true);
});
