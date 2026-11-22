import { createStore } from '../src/store.js';
import { createBundledLoader } from '../src/bundled.js';
import { createUnlockUi } from './unlock.js';
import { createKanbanStorage } from '../src/kanban/storage.js';
import { fetchKanbanContext } from '../src/kanban/client.js';

const store = createStore(chrome.storage.local);
const bundled = createBundledLoader((name) =>
  fetch(chrome.runtime.getURL(`blocklists/${name}.txt`)).then((r) => {
    if (!r.ok) throw new Error(String(r.status));
    return r.text();
  }));

const $ = (id) => document.getElementById(id);
const unlock = createUnlockUi(store);
const kanbanStorage = createKanbanStorage(chrome.storage.local);

/** Runs a store change; a weakening change while locked opens the unlock dialog instead. */
async function guarded(change) {
  try {
    await change();
    return true;
  } catch (e) {
    if (e.message !== 'locked') throw e;
    await unlock.show();
    return false;
  }
}
let state = { categories: [], stats: {} };
let editingId = null;

async function render() {
  state = await store.getState();
  const list = $('categories');
  list.replaceChildren(...state.categories.map((c) => {
    const li = document.createElement('li');
    li.className = 'category';
    li.innerHTML = '<div class="info"><div class="name"></div><div class="count"></div></div><input type="checkbox" class="toggle" title="Включить или выключить">';
    li.querySelector('.name').textContent = c.name;
    li.querySelector('.count').textContent = `Заблокировано: ${state.stats[c.id] ?? 0}`;
    const toggle = li.querySelector('.toggle');
    toggle.checked = c.enabled;
    toggle.addEventListener('click', (e) => e.stopPropagation());
    toggle.addEventListener('change', async () => {
      if (!(await guarded(() => store.setEnabled(c.id, toggle.checked)))) toggle.checked = c.enabled;
    });
    li.addEventListener('click', () => openEditor(c));
    return li;
  }));
}

async function openEditor(category) {
  editingId = category?.id ?? null;
  $('editor-title').textContent = category ? category.name : 'Новая категория';
  $('name').value = category?.name ?? '';
  $('keywords').value = (category?.keywords ?? []).join('\n');
  $('domains').value = (category?.domains ?? []).join('\n');
  $('prefixes').value = (category?.urlPrefixes ?? []).join('\n');
  $('only-hosts').value = (category?.onlyHosts ?? []).join(', ');
  $('delete').hidden = !category;
  $('bundled').hidden = true;
  if (category?.bundledList) {
    const domains = (await bundled.get(category.bundledList)).size;
    const words = [...(await bundled.get(`${category.bundledList}-keywords`))];
    const parts = [`${words.length.toLocaleString('ru-RU')} слов и фраз`];
    if (domains > 0) parts.push(`${domains.toLocaleString('ru-RU')} сайтов`);
    $('bundled').querySelector('summary').textContent = `Встроенный словарь: ${parts.join(' + ')}`;
    $('bundled-words').textContent = words.join(', ');
    $('bundled').open = false;
    $('bundled').hidden = false;
  }
  updateSave();
  $('editor').hidden = false;
  $('name').focus();
}

function closeEditor() {
  $('editor').hidden = true;
  editingId = null;
}

function updateSave() {
  $('save').disabled = $('name').value.trim() === '';
}

function readKanbanForm() {
  return {
    enabled: $('kanban-enabled').checked,
    baseUrl: $('kanban-url').value,
    token: $('kanban-token').value,
    boardId: $('kanban-board').value,
    columnId: $('kanban-column').value,
    limit: $('kanban-limit').value,
  };
}

function setKanbanStatus(message, error = false) {
  const status = $('kanban-status');
  status.textContent = message;
  status.classList.toggle('warning', error);
}

async function loadKanbanSettings() {
  const settings = await kanbanStorage.get();
  $('kanban-enabled').checked = settings.enabled;
  $('kanban-url').value = settings.baseUrl;
  $('kanban-token').value = settings.token;
  $('kanban-board').value = settings.boardId;
  $('kanban-column').value = settings.columnId;
  $('kanban-limit').value = settings.limit;
}

async function requestKanbanPermission(baseUrl) {
  const origin = new URL(baseUrl).origin;
  if (!chrome.permissions?.request) return;
  const granted = await chrome.permissions.request({ origins: [`${origin}/*`] });
  if (!granted) throw new Error('Доступ к адресу Kanban не разрешён');
}

async function saveKanbanSettings() {
  try {
    const settings = await kanbanStorage.save(readKanbanForm());
    setKanbanStatus(settings.enabled ? 'Подключение сохранено.' : 'Подключение сохранено и выключено.');
    return settings;
  } catch (error) {
    setKanbanStatus(error.message, true);
    return null;
  }
}

$('kanban-save').addEventListener('click', () => saveKanbanSettings());
$('kanban-test').addEventListener('click', async () => {
  const button = $('kanban-test');
  button.disabled = true;
  try {
    const settings = await saveKanbanSettings();
    if (!settings) return;
    await requestKanbanPermission(settings.baseUrl);
    const context = await fetchKanbanContext(settings);
    setKanbanStatus(`Связь работает. Получено карточек: ${context.cards?.length ?? 0}.`);
  } catch (error) {
    setKanbanStatus(error.message, true);
  } finally {
    button.disabled = false;
  }
});

$('add').addEventListener('click', () => openEditor(null));
$('name').addEventListener('input', updateSave);
$('cancel').addEventListener('click', closeEditor);
$('save').addEventListener('click', async () => {
  if ($('name').value.trim() === '') return;
  const saved = await guarded(() => store.saveCategory(
    editingId, $('name').value, $('keywords').value, $('domains').value, $('prefixes').value, $('only-hosts').value,
  ));
  if (saved) closeEditor();
});
$('delete').addEventListener('click', async () => {
  const c = state.categories.find((x) => x.id === editingId);
  if (c && confirm(`Удалить категорию «${c.name}» вместе со статистикой?`)) {
    if (await guarded(() => store.deleteCategory(c.id))) closeEditor();
  }
});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area !== 'local') return;
  if (changes.categories || changes.stats) $('version').textContent = chrome.runtime.getManifest().version;

render();
unlock.renderStatus();
loadKanbanSettings();
  if (changes.lock) unlock.renderStatus();
});

chrome.extension.isAllowedIncognitoAccess().then((allowed) => { $('incognito').hidden = allowed; });

$('version').textContent = chrome.runtime.getManifest().version;

render();
unlock.renderStatus();
