// First-run wizard: categories to block and the protection mode.
import { createStore } from '../src/store.js';
import { isUnlocked, setPartnerMode, setPauseMode } from '../src/core/lock.js';

const store = createStore(chrome.storage.local);
const $ = (id) => document.getElementById(id);
const mode = () => document.querySelector('input[name=mode]:checked').value;

function randomSalt() {
  return [...crypto.getRandomValues(new Uint8Array(16))].map((b) => b.toString(16).padStart(2, '0')).join('');
}

async function init() {
  const lock = await store.getLock();
  if (!isUnlocked(lock, Date.now())) {
    $('locked').hidden = false;
    $('wizard').hidden = true;
    return;
  }
  const { categories } = await store.getState();
  $('cat-porn').checked = categories.find((c) => c.id === 'porn')?.enabled ?? true;
  $('cat-youtube').checked = categories.find((c) => c.id === 'youtube-fun')?.enabled ?? false;
  if (lock.mode) document.querySelector(`input[name=mode][value=${lock.mode}]`).checked = true;
  $('pause-minutes').value = String(lock.pauseMinutes);
  syncMode();
  $('incognito-ok').hidden = !(await chrome.extension.isAllowedIncognitoAccess());
}

function syncMode() {
  $('pin-fields').hidden = mode() !== 'partner';
}

document.querySelectorAll('input[name=mode]').forEach((r) => r.addEventListener('change', syncMode));

$('wizard').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('error').textContent = '';
  try {
    const lock = await store.getLock();
    let next;
    if (mode() === 'pause') {
      next = setPauseMode(lock, Number($('pause-minutes').value));
    } else {
      if ($('pin').value !== $('pin2').value) throw new Error('PIN не совпадает');
      if (!/^\d{4,8}$/.test($('pin').value)) throw new Error('PIN — от 4 до 8 цифр');
      next = await setPartnerMode(lock, $('pin').value, randomSalt());
    }
    // Categories first: protection is still open while the wizard runs.
    const { categories } = await store.getState();
    for (const [id, box] of [['porn', 'cat-porn'], ['youtube-fun', 'cat-youtube']]) {
      if (categories.some((c) => c.id === id)) await store.setEnabled(id, $(box).checked);
    }
    await store.saveLock(next);
    $('wizard').hidden = true;
    $('done').hidden = false;
  } catch (err) {
    $('error').textContent = err.message === 'locked' ? 'Изменения сейчас закрыты' : err.message;
  }
});

init();
