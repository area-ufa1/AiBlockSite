import test from 'node:test';
import assert from 'node:assert/strict';
import { createHandoffStore, KEY_PREFIX, MAX_AGE_MS } from '../extension/src/handoff.js';
import { chatgptHandoffUrl } from '../extension/src/core/prompt.js';

function memoryArea() {
  const data = {};
  return {
    async get(key) { return { [key]: data[key] }; },
    async set(values) { Object.assign(data, values); },
    async remove(key) { delete data[key]; },
  };
}

test('handoff stores prompt behind a nonce and binds reads to the source tab', async () => {
  const area = memoryArea();
  let now = 1000;
  const store = createHandoffStore(area, () => now, () => '12345678-1234-1234-1234-123456789abc');
  const record = await store.create('private prompt', { tabId: 7, sourceUrl: 'https://youtube.com/watch?v=1' });
  assert.equal(record.nonce, '12345678-1234-1234-1234-123456789abc');
  assert.equal((await store.get(record.nonce, 8)), null);
  assert.equal((await store.get(record.nonce, 7)).prompt, 'private prompt');
  await store.update(record.nonce, { status: 'sent' });
  assert.equal((await store.get(record.nonce, 7)).status, 'sent');
  now += MAX_AGE_MS + 1;
  assert.equal(await store.get(record.nonce, 7), null);
});

test('handoff URL contains only the nonce', () => {
  const url = chatgptHandoffUrl('12345678-1234-1234-1234-123456789abc');
  assert.equal(url, 'https://chatgpt.com/?tabguard_handoff=12345678-1234-1234-1234-123456789abc');
  assert.ok(url.startsWith('https://chatgpt.com/'));
  assert.ok(!url.includes(KEY_PREFIX));
});
