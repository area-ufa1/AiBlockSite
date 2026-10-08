import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createBundledLoader } from '../extension/src/bundled.js';

test('bundled loader caches and survives fetch failure', async () => {
  let calls = 0;
  let fail = true;
  const loader = createBundledLoader(async () => {
    calls++;
    if (fail) throw new Error('x');
    return '# c\npornhub.com\n\n  xvideos.com  \n';
  });
  assert.equal((await loader.get('adult')).size, 0);
  fail = false;
  assert.deepEqual([...(await loader.get('adult'))], ['pornhub.com', 'xvideos.com']);
  await loader.get('adult');
  assert.equal(calls, 2);
});
