// Isolated content script on YouTube: forwards the video category to the service worker and
// hides recommendations while «YouTube: развлечения» is on (its switch is lock-protected).
window.addEventListener('message', (e) => {
  if (e.source !== window || e.data?.source !== 'tabguard-youtube' || typeof e.data.genre !== 'string') return;
  chrome.runtime.sendMessage({ type: 'youtube-genre', genre: e.data.genre }).catch(() => {});
});

async function syncCalmMode() {
  const { categories } = await chrome.storage.local.get('categories');
  const on = (categories ?? []).some((c) => c.enabled && c.bundledList === 'youtube-fun');
  document.documentElement.classList.toggle('tabguard-calm', on);
}

chrome.storage.onChanged.addListener((changes) => {
  if (changes.categories) syncCalmMode();
});
syncCalmMode();
