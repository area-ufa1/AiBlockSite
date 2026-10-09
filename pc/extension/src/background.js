// Service worker: watches navigations and titles, sends matching tabs to Claude.
import { decide } from './core/decide.js';
import { isAllowlisted } from './core/matcher.js';
import { safeSearchUrl } from './core/safesearch.js';
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

function bundledNames(categories) {
  return [...new Set(categories.filter((c) => c.enabled && c.bundledList).map((c) => c.bundledList))];
}

/** Built-in domain sets per bundled list (`blocklists/<name>.txt`). */
async function bundledFor(categories) {
  const names = bundledNames(categories);
  return new Map(await Promise.all(names.map(async (n) => [n, await bundled.get(n)])));
}

/** Built-in keywords per bundled list (`blocklists/<name>-keywords.txt`). */
async function bundledKeywordsFor(categories) {
  const names = bundledNames(categories);
  return new Map(await Promise.all(names.map(async (n) => [n, [...(await bundled.get(`${n}-keywords`))]])));
}

async function check(tabId, url, title) {
  try {
    if (!url || !/^https?:/i.test(url)) return;
    const { categories } = await store.getState();
    const verdict = decide({ url, title }, categories, await bundledFor(categories), await bundledKeywordsFor(categories));
    if (!verdict) {
      const safe = safeSearchUrl(url);
      if (safe) await chrome.tabs.update(tabId, { url: safe });
      return;
    }
    if (!cooldown.shouldBlock(tabId, url)) {
      // Blocked a moment ago. If the tab is already heading to Claude this is the second trigger
      // of one visit; otherwise the user came straight back (Back, same link) — send it again, uncounted.
      const tab = await chrome.tabs.get(tabId);
      if (!isAllowlisted(tab.pendingUrl || tab.url)) await chrome.tabs.update(tabId, { url: verdict.redirectUrl });
      return;
    }
    try {
      await chrome.tabs.update(tabId, { url: verdict.redirectUrl });
      ensureRedirected(tabId, verdict.redirectUrl);
    } catch (e) {
      cooldown.forget(tabId, url); // let the title check retry
      throw e;
    }
    await Promise.allSettled([copyToClipboard(verdict.prompt), store.incrementStat(verdict.categoryId)]);
  } catch (e) {
    console.error('Страж вкладок:', e);
  }
}

/**
 * Chrome occasionally drops a redirect issued while the original navigation is still starting
 * (the tab ends up blank). Check once shortly after and redirect again if the tab is not on Claude.
 */
function ensureRedirected(tabId, redirectUrl) {
  setTimeout(async () => {
    try {
      const tab = await chrome.tabs.get(tabId);
      if (!isAllowlisted(tab.pendingUrl || tab.url)) await chrome.tabs.update(tabId, { url: redirectUrl });
    } catch {
      // Tab closed meanwhile.
    }
  }, 1500);
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

// Title changes, but also URL changes and finished loads: going Back restores the old title
// without a title event, and single-page sites change the URL without a new navigation.
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.title || changeInfo.url || changeInfo.status === 'complete') check(tabId, tab.url, tab.title ?? null);
});

chrome.action.onClicked.addListener(() => chrome.runtime.openOptionsPage());

chrome.runtime.onInstalled.addListener(() => store.getState());
