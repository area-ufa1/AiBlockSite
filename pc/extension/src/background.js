// Service worker: watches navigations and titles, sends matching tabs to Claude.
import { decide } from './core/decide.js';
import { BlockCooldown } from './core/cooldown.js';
import { createStore } from './store.js';
import { createBundledLoader } from './bundled.js';

const store = createStore(chrome.storage.local);
const bundled = createBundledLoader((name) =>
  fetch(chrome.runtime.getURL(`blocklists/${name}.txt`)).then((r) => {
    if (!r.ok) throw new Error(`blocklist ${name}: ${r.status}`);
    return r.text();
  }));
const cooldown = new BlockCooldown();

async function bundledFor(categories) {
  const names = [...new Set(categories.filter((c) => c.enabled && c.bundledList).map((c) => c.bundledList))];
  return new Map(await Promise.all(names.map(async (n) => [n, await bundled.get(n)])));
}

async function check(tabId, url, title) {
  try {
    if (!url || !/^https?:/i.test(url)) return;
    const { categories } = await store.getState();
    const verdict = decide({ url, title }, categories, await bundledFor(categories));
    if (!verdict || !cooldown.shouldBlock(tabId, url)) return;
    await chrome.tabs.update(tabId, { url: verdict.redirectUrl });
    await Promise.allSettled([copyToClipboard(verdict.prompt), store.incrementStat(verdict.categoryId)]);
  } catch (e) {
    console.error('Страж вкладок:', e);
  }
}

async function copyToClipboard(text) {
  if (!(await chrome.offscreen.hasDocument())) {
    await chrome.offscreen.createDocument({
      url: 'src/offscreen.html',
      reasons: ['CLIPBOARD'],
      justification: 'Copy the supportive prompt so it can be pasted into Claude',
    });
  }
  await chrome.runtime.sendMessage({ target: 'offscreen', type: 'copy', text });
}

chrome.webNavigation.onBeforeNavigate.addListener((d) => {
  if (d.frameId === 0) check(d.tabId, d.url, null);
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.title) check(tabId, tab.url, changeInfo.title);
});

chrome.action.onClicked.addListener(() => chrome.runtime.openOptionsPage());

chrome.runtime.onInstalled.addListener(() => store.getState());
