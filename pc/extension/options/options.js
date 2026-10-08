import { createStore } from '../src/store.js';
import { createBundledLoader } from '../src/bundled.js';

const store = createStore(chrome.storage.local);
const bundled = createBundledLoader((name) =>
  fetch(chrome.runtime.getURL(`blocklists/${name}.txt`)).then((r) => {
    if (!r.ok) throw new Error(String(r.status));
    return r.text();
  }));

const $ = (id) => document.getElementById(id);
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
    toggle.addEventListener('change', () => store.setEnabled(c.id, toggle.checked));
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
  $('delete').hidden = !category;
  $('bundled').hidden = true;
  if (category?.bundledList) {
    const size = (await bundled.get(category.bundledList)).size;
    $('bundled').textContent = `+ встроенный список (${size.toLocaleString('ru-RU')} доменов)`;
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

$('add').addEventListener('click', () => openEditor(null));
$('name').addEventListener('input', updateSave);
$('cancel').addEventListener('click', closeEditor);
$('save').addEventListener('click', async () => {
  if ($('name').value.trim() === '') return;
  await store.saveCategory(editingId, $('name').value, $('keywords').value, $('domains').value);
  closeEditor();
});
$('delete').addEventListener('click', async () => {
  const c = state.categories.find((x) => x.id === editingId);
  if (c && confirm(`Удалить категорию «${c.name}» вместе со статистикой?`)) {
    await store.deleteCategory(c.id);
    closeEditor();
  }
});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === 'local') render();
});

chrome.extension.isAllowedIncognitoAccess().then((allowed) => { $('incognito').hidden = allowed; });

render();
