import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const lines = (p) => readFileSync(new URL(p, import.meta.url), 'utf8').split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('#'));

test('bundled list has no excluded mainstream sites', () => {
  const list = new Set(lines('../../app/src/main/assets/blocklists/adult.txt'));
  const leaked = lines('../../scripts/blocklist-exclude.txt').filter((d) => list.has(d));
  assert.deepEqual(leaked, []);
});

test('bundled list still blocks known adult sites', () => {
  const list = new Set(lines('../../app/src/main/assets/blocklists/adult.txt'));
  assert.ok(list.has('pornhub.com'));
});
