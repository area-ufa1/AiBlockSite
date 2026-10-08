# Страж вкладок для ПК — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Расширение Manifest V3 для Chrome и Яндекс.Браузера, которое по категориям (слова, домены, встроенный список) уводит вкладку в Claude с поддерживающим промптом, плюс установка через `install.bat`.

**Architecture:** Вся логика — чистые ES-модули в `pc/extension/src/core/` (без `chrome.*`), тестируются `node --test`. Фоновый service worker (`background.js`) склеивает события браузера, хранилище и логику; страница настроек — обычный HTML/JS. Сквозная проверка — Playwright загружает расширение в Chromium и проверяет перенаправление.

**Tech Stack:** JavaScript (ES2022, ES-модули, без сборки), Chrome Extensions MV3 API, `node --test` (Node 22), Playwright 1.56 (глобально в контейнере) для smoke-теста, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-08-tab-guard-pc-design.md`

## Global Constraints

- Папка `pc/extension/` загружается в браузер как есть — в ней только файлы расширения (без тестов, без `package.json`).
- Разрешения ровно: `storage`, `tabs`, `webNavigation`, `offscreen`, `clipboardWrite`; `"incognito": "spanning"`; без `host_permissions` и content scripts.
- Логика совпадений и тексты — как в Android `core/` (тот же промпт `SupportPrompt.build`, та же ссылка `https://claude.ai/new?q=` с `%20` вместо `+`).
- Исключения: хосты `claude.ai`, `anthropic.com` и их поддомены; любые адреса не `http:`/`https:` не проверяются вообще.
- Повторная блокировка той же вкладки с тем же адресом — не чаще раза в **2000 мс**.
- Категория по умолчанию: «Порно», включена, `bundledList: "adult"`, слова `порно, porn, xxx, секс, sex, хентай, hentai, nsfw, onlyfans, эротика`.
- Встроенный список берётся из `app/src/main/assets/blocklists/adult.txt` и копируется в архив при упаковке (в `pc/extension/` в git не лежит).
- Весь интерфейс на русском.

## Review Focus

1. **Петля на claude.ai** — адрес `claude.ai/new?q=порно` никогда не блокируется. Тест: Task 1, `allowlisted hosts are never blocked`.
2. **Свои и служебные страницы** (`chrome://extensions`, `browser://tune`, `chrome-extension://…/options.html`, `file://`) никогда не блокируются, иначе можно запереть себя вне настроек. Тест: Task 2, `non-http urls are never blocked`.
3. **Двойное срабатывание** (до загрузки + по заголовку) не переводит вкладку дважды. Тест: Task 2, `same tab and url within 2 s is blocked once`.
4. **Обновление расширения** не затирает категории и статистику человека значением по умолчанию. Тест: Task 3, `existing categories are not reseeded`.
5. **Сон service worker'а** — после пробуждения встроенный список снова доступен, а сбой чтения файла даёт пустой список, а не падение. Тест: Task 3, `bundled loader caches and survives fetch failure`.

---

## Структура файлов

```
pc/
  package.json                 — {"type":"module","private":true,"scripts":{"test":"node --test test/"}}
  install.bat, УСТАНОВКА.txt   — кладутся в архив рядом с TabGuard/
  scripts/package.sh           — собирает dist/ (расширение + список + bat + txt)
  e2e/smoke.cjs                — Playwright: загрузить расширение, проверить перенаправление
  test/*.test.js               — node --test
  extension/
    manifest.json
    icons/icon16.png icon32.png icon48.png icon128.png
    src/core/text.js           — normalize, tokenize, extractHost
    src/core/matcher.js        — isAllowlisted, matchTab
    src/core/prompt.js         — buildSupportPrompt, claudeUrl
    src/core/categories.js     — DEFAULT_CATEGORIES, parseKeywords, parseDomains
    src/core/cooldown.js       — BlockCooldown
    src/core/decide.js         — decide
    src/store.js               — createStore(area)
    src/bundled.js             — createBundledLoader(fetchText)
    src/background.js          — service worker
    src/offscreen.html, src/offscreen.js — копирование в буфер
    options/options.html, options.css, options.js
docs/manual-test-pc.md
.github/workflows/android.yml  — + job `extension`
```

Импорт в тестах: `import { … } from '../extension/src/core/text.js'`.

---

### Task 1: Текст и сопоставление (порт с Android)

**Files:**
- Create: `pc/package.json`, `pc/extension/src/core/text.js`, `pc/extension/src/core/matcher.js`
- Test: `pc/test/text.test.js`, `pc/test/matcher.test.js`

**Interfaces:**
- Produces:
  - `normalize(s: string): string` — `toLowerCase()`, `ё→е`, `trim()`
  - `tokenize(s: string): string[]` — `normalize`, split по `/[^\p{L}\p{N}]+/u`, без пустых
  - `extractHost(url: string): string | null` — как `core/.../text/Text.kt`: схема необязательна, без `www.`, порта, userinfo; `null`, если не хост с точкой
  - `isAllowlisted(url: string | null): boolean` — хост `claude.ai`/`anthropic.com` или их поддомен
  - `matchTab(tab: {url: string|null, title: string|null}, categories: Category[], bundled: Map<string, Set<string>>): {categoryId: string, categoryName: string, source: 'domain'|'keyword'} | null`
  - `Category = {id: string, name: string, enabled: boolean, keywords: string[], domains: string[], bundledList: string|null}`

Алгоритм `matchTab` — как `LocalMatcher.match`: только `enabled`; сначала домены у всех категорий (суффиксы хоста ищутся в `category.domains` и в `bundled.get(category.bundledList)`), потом слова (токены заголовка и адреса отдельно; слово-фраза = подряд идущие токены, каждый начинается с соответствующего токена слова; пустые слова пропускаются).

- [ ] **Step 1: Тесты** — перенести 1:1 кейсы из `core/src/test/.../TextTest.kt` и `LocalMatcherTest.kt` (имена те же), плюс:

```js
test('bundled list domains match', () => {
  const cat = { id: 'p', name: 'Порно', enabled: true, keywords: [], domains: [], bundledList: 'adult' };
  const bundled = new Map([['adult', new Set(['pornhub.com'])]]);
  assert.deepEqual(matchTab({ url: 'https://rt.pornhub.com/v', title: null }, [cat], bundled),
    { categoryId: 'p', categoryName: 'Порно', source: 'domain' });
});
test('search query in url matches keyword', () => {
  assert.equal(matchTab({ url: 'https://www.google.com/search?q=free+porn', title: null }, [porn], new Map())?.source, 'keyword');
});
```

- [ ] **Step 2: Run** `cd pc && npm test` → FAIL (модули не найдены)
- [ ] **Step 3: Реализовать** `text.js`, `matcher.js`
- [ ] **Step 4: Run** `cd pc && npm test` → PASS
- [ ] **Step 5: Commit** `git commit -m "pc: text normalization and matcher (port of Android core)"`

---

### Task 2: Промпт, разбор ввода, защита от повторов, решение

**Files:**
- Create: `pc/extension/src/core/prompt.js`, `categories.js`, `cooldown.js`, `decide.js`
- Test: `pc/test/prompt.test.js`, `categories.test.js`, `cooldown.test.js`, `decide.test.js`

**Interfaces:**
- Consumes: Task 1
- Produces:
  - `buildSupportPrompt(tab: {url, title}, categoryName: string): string` — текст 1:1 с `SupportPrompt.build` (страница = title, если не пустой, иначе url; обрезка до 200)
  - `claudeUrl(prompt: string): string` — `'https://claude.ai/new?q=' + encodeURIComponent(prompt)`
  - `DEFAULT_CATEGORIES: Category[]` — одна категория из Global Constraints, `id: 'porn'`
  - `parseKeywords(text: string): string[]` — разделители `\n` и `,`, `normalize`, без пустых, без дублей
  - `parseDomains(text: string): string[]` — то же + `extractHost`, не-хосты отбрасываются
  - `class BlockCooldown { constructor(ms = 2000, now = () => Date.now()); shouldBlock(tabId: number, url: string): boolean }` — `false`, если та же пара `(tabId, url)` блокировалась < `ms` назад; при `true` запоминает
  - `decide(tab: {url, title}, categories, bundled): {categoryId, categoryName, prompt, redirectUrl} | null` — `null` для не-http(s) и allowlist; иначе `matchTab` + промпт + `claudeUrl`

- [ ] **Step 1: Тесты**

```js
test('claude url encodes spaces as %20', () => assert.equal(claudeUrl('а b'), 'https://claude.ai/new?q=%D0%B0%20b'));
test('support prompt names category and title', () =>
  assert.ok(buildSupportPrompt({ url: 'x.com', title: 'Видео' }, 'Порно').startsWith('Я только что чуть не открыл страницу из категории «Порно»: «Видео».')));
test('parseKeywords drops blanks and duplicates', () => assert.deepEqual(parseKeywords('Порно,\n\n XXX ,xxx'), ['порно', 'xxx']));
test('parseDomains normalizes urls', () => assert.deepEqual(parseDomains('https://www.X.com/\nне домен'), ['x.com']));
test('same tab and url within 2 s is blocked once', () => {
  let t = 0; const c = new BlockCooldown(2000, () => t);
  assert.equal(c.shouldBlock(1, 'u'), true); t = 1500;
  assert.equal(c.shouldBlock(1, 'u'), false); t = 2500;
  assert.equal(c.shouldBlock(1, 'u'), true);
  assert.equal(c.shouldBlock(2, 'u'), true);
});
test('non-http urls are never blocked', () => {
  for (const url of ['chrome://extensions', 'browser://tune', 'chrome-extension://abc/options/options.html', 'file:///C:/porn.html'])
    assert.equal(decide({ url, title: 'porn' }, DEFAULT_CATEGORIES, new Map()), null);
});
test('decide returns redirect to claude', () => {
  const d = decide({ url: 'https://x.com/porn', title: null }, DEFAULT_CATEGORIES, new Map());
  assert.equal(d.categoryId, 'porn'); assert.ok(d.redirectUrl.startsWith('https://claude.ai/new?q='));
});
```

- [ ] **Step 2: Run** `cd pc && npm test` → FAIL
- [ ] **Step 3: Реализовать** четыре модуля
- [ ] **Step 4: Run** `cd pc && npm test` → PASS
- [ ] **Step 5: Commit** `git commit -m "pc: prompt, input parsing, cooldown and block decision"`

---

### Task 3: Хранилище и встроенный список

**Files:**
- Create: `pc/extension/src/store.js`, `pc/extension/src/bundled.js`
- Test: `pc/test/store.test.js`, `pc/test/bundled.test.js`

**Interfaces:**
- Consumes: Task 2 (`DEFAULT_CATEGORIES`, `parseKeywords`, `parseDomains`)
- Produces:
  - `createStore(area)` — `area` с `get(keys): Promise<object>` и `set(obj): Promise<void>` (как `chrome.storage.local`); методы:
    - `getState(): Promise<{categories: Category[], stats: Record<string, number>}>` — при отсутствии `categories` сохраняет и возвращает `DEFAULT_CATEGORIES`
    - `saveCategory(id: string|null, name: string, keywordsText: string, domainsText: string): Promise<string>` — новая: `id = crypto.randomUUID()`, `enabled: true`, `bundledList: null`; существующая: сохраняет `enabled` и `bundledList`
    - `setEnabled(id, enabled): Promise<void>`, `deleteCategory(id): Promise<void>` (удаляет и её статистику), `incrementStat(id): Promise<void>`
  - `createBundledLoader(fetchText: (name) => Promise<string>)` → `{ get(name): Promise<Set<string>> }` — разбор: строки, `trim`, без пустых и `#`; результат кэшируется; ошибка `fetchText` → пустой `Set` (не кэшируется)

- [ ] **Step 1: Тесты** (фейковый `area` на `Map`)

```js
test('first run seeds default category', async () => { /* getState → categories[0].name === 'Порно', stats {} */ });
test('existing categories are not reseeded', async () => { /* area уже содержит categories:[{id:'a',…}] → getState возвращает их без 'porn' */ });
test('saveCategory keeps enabled and bundledList of existing', async () => { /* setEnabled('porn', false); saveCategory('porn','Порно','porn','') → enabled false, bundledList 'adult', keywords ['porn'] */ });
test('deleteCategory removes its stats', async () => { /* incrementStat x2, delete → stats без ключа */ });
test('incrementStat counts per category', async () => { /* 2 и 1 */ });
test('bundled loader caches and survives fetch failure', async () => {
  let calls = 0, fail = true;
  const l = createBundledLoader(async () => { calls++; if (fail) throw new Error('x'); return '# c\npornhub.com\n\n'; });
  assert.equal((await l.get('adult')).size, 0); fail = false;
  assert.deepEqual([...(await l.get('adult'))], ['pornhub.com']); await l.get('adult');
  assert.equal(calls, 2);
});
```

- [ ] **Step 2: Run** `cd pc && npm test` → FAIL
- [ ] **Step 3: Реализовать** `store.js`, `bundled.js`
- [ ] **Step 4: Run** `cd pc && npm test` → PASS
- [ ] **Step 5: Commit** `git commit -m "pc: storage and bundled blocklist loader"`

---

### Task 4: Фоновый скрипт, буфер обмена, манифест, иконки + smoke-тест

**Files:**
- Create: `pc/extension/manifest.json`, `src/background.js`, `src/offscreen.html`, `src/offscreen.js`, `icons/icon{16,32,48,128}.png`, `pc/e2e/smoke.cjs`, `pc/scripts/package.sh`

**Interfaces:**
- Consumes: `decide` (Task 2), `createStore`, `createBundledLoader` (Task 3)

`manifest.json`: `manifest_version: 3`, `name: "Страж вкладок"`, `version: "0.1.0"`, `background: {service_worker: "src/background.js", type: "module"}`, `action: {default_title: "Страж вкладок", default_icon}` (без popup), `options_ui: {page: "options/options.html", open_in_tab: true}`, `icons`, разрешения и `incognito` — из Global Constraints.

`background.js`:
- `store = createStore(chrome.storage.local)`; `bundled = createBundledLoader(name => fetch(chrome.runtime.getURL(\`blocklists/${name}.txt\`)).then(r => r.text()))`; `cooldown = new BlockCooldown()`.
- `check(tabId, url, title)`: `getState()` → `Map` загруженных списков для `bundledList` включённых категорий → `decide` → при результате и `cooldown.shouldBlock(tabId, url)`: `chrome.tabs.update(tabId, {url: redirectUrl})`, `copyToClipboard(prompt)`, `store.incrementStat(categoryId)`. Любая ошибка ловится и пишется в `console.error`, не роняя слушатели.
- Слушатели: `webNavigation.onBeforeNavigate` (`frameId === 0`) → `check(tabId, url, null)`; `tabs.onUpdated` с `changeInfo.title` → `check(tabId, tab.url, changeInfo.title)`; `action.onClicked` → `runtime.openOptionsPage()`; `runtime.onInstalled` → `store.getState()` (засеять).
- `copyToClipboard(text)`: создать offscreen-документ `src/offscreen.html` (reasons `['CLIPBOARD']`), если его нет (`chrome.offscreen.hasDocument`), и отправить `{type: 'copy', text}`; `offscreen.js` копирует через скрытый `textarea` + `document.execCommand('copy')`.

Иконки: отрисовать тот же щит, что у Android (`docs/icon-preview.png` / `app/src/main/res/drawable/ic_launcher_*.xml`, фон с градиентом, скруглённый квадрат) в PNG 16/32/48/128 через `/opt/pw-browsers/chromium --headless --screenshot`.

`scripts/package.sh` → `pc/dist/`: `TabGuard/` (= `extension/` + `blocklists/adult.txt` из `app/src/main/assets/blocklists/`), `install.bat`, `УСТАНОВКА.txt` (последние два — Task 6; до этого скрипт их пропускает, если файлов нет).

`e2e/smoke.cjs` (запуск: `NODE_PATH=/opt/node22/lib/node_modules node pc/e2e/smoke.cjs`): `package.sh` → `chromium.launchPersistentContext('', {headless: true, executablePath: '/opt/pw-browsers/chromium', args: ['--headless=new', '--disable-extensions-except=…/dist/TabGuard', '--load-extension=…/dist/TabGuard']})`; ждёт service worker; открывает `https://example.com/search?q=porn`; ждёт, пока `page.url()` начнётся с `https://claude.ai/new?q=` (сеть не обязательна — проверяется адрес). Второй сценарий: `https://example.com/` не перенаправляется в течение 3 с.

- [ ] **Step 1: Написать** `e2e/smoke.cjs` и `package.sh`
- [ ] **Step 2: Run** `NODE_PATH=/opt/node22/lib/node_modules node pc/e2e/smoke.cjs` → FAIL (нет `manifest.json`)
- [ ] **Step 3: Реализовать** манифест, `background.js`, offscreen, иконки
- [ ] **Step 4: Run** smoke → `PASS: blocked → claude.ai; PASS: example.com untouched`; `cd pc && npm test` → PASS
- [ ] **Step 5: Commit** `git commit -m "pc: background worker, clipboard, manifest, icons, e2e smoke"`

---

### Task 5: Страница настроек

**Files:**
- Create: `pc/extension/options/options.html`, `options.css`, `options.js`
- Modify: `pc/e2e/smoke.cjs` (третий сценарий)

**Interfaces:**
- Consumes: `createStore` (Task 3), `createBundledLoader` (для подписи «+ встроенный список (N доменов)»)

Содержимое — по спеке, раздел «Страница настроек». Статус: «Защита работает»; если `await chrome.extension.isAllowedIncognitoAccess()` = `false` — предупреждение «Включите „Разрешить в режиме инкогнито“ в карточке расширения». Список перерисовывается по `chrome.storage.onChanged`. Редактирование — форма на той же странице (название, два `textarea`, «Сохранить» неактивна при пустом названии, «Удалить» через `confirm`). Стиль: светлый фон, карточки, акцент — градиент иконки (`#4338CA → #0E9F9A`), без внешних библиотек.

- [ ] **Step 1: Добавить в smoke** сценарий: открыть `chrome-extension://<id>/options/options.html`, найти текст «Порно» и «Заблокировано: 1» (после первого сценария); добавить категорию «Тест» со словом `zzzword`, открыть `https://example.com/zzzword` → перенаправление в Claude
- [ ] **Step 2: Run** smoke → FAIL (нет страницы)
- [ ] **Step 3: Реализовать** страницу
- [ ] **Step 4: Run** smoke → все сценарии PASS
- [ ] **Step 5: Commit** `git commit -m "pc: options page"`

---

### Task 6: Установщик, CI-артефакт, чек-лист

**Files:**
- Create: `pc/install.bat`, `pc/УСТАНОВКА.txt`, `docs/manual-test-pc.md`
- Modify: `.github/workflows/android.yml` (job `extension`)

`install.bat` (UTF-8, первая строка `@echo off`, затем `chcp 65001 >nul`): копирует `%~dp0TabGuard` в `%LOCALAPPDATA%\TabGuard` (`rmdir /s /q`, затем `xcopy /e /i /q /y`), кладёт путь в буфер (`<nul set /p="%LOCALAPPDATA%\TabGuard" | clip`), выполняет `start "" chrome "chrome://extensions"` (ошибку игнорирует), печатает шаги установки и обновления из спеки, `pause`.

`УСТАНОВКА.txt` — те же шаги, плюс Яндекс.Браузер (`browser://extensions`) и «Разрешить в режиме инкогнито».

Job `extension` (ubuntu-latest): `actions/setup-node@v4` (22) → `cd pc && npm test` → `bash pc/scripts/package.sh` → `actions/upload-artifact@v4` name `tab-guard-extension`, path `pc/dist/`.

`docs/manual-test-pc.md` — чек-лист из спеки (раздел «Проверка»), таблица Chrome / Яндекс / инкогнито.

- [ ] **Step 1: Run** `bash pc/scripts/package.sh && ls pc/dist` → есть `TabGuard/manifest.json`, `TabGuard/blocklists/adult.txt`, `install.bat`, `УСТАНОВКА.txt`
- [ ] **Step 2: Run** `file pc/install.bat` → UTF-8 без BOM; `grep -c chcp pc/install.bat` → 1
- [ ] **Step 3: Commit, push**, дождаться зелёного job `extension` в CI и наличия артефакта `tab-guard-extension`
