# Проверка через Claude на домашнем сервере — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Сервер на Python, который классифицирует страницы через `claude -p` под подпиской, и клиенты в Android-приложении и расширении, которые спрашивают его во вторую очередь.

**Architecture:** `server/` — сервис на стандартной библиотеке Python: HTTP (`ThreadingHTTPServer`) → проверка токена → кэш SQLite → один вызов `claude` за раз со склейкой одинаковых запросов. Клиенты сначала проверяют локально; если не заблокировано — строят запрос `{kind: search|site, …}` по общим правилам (Kotlin в `:core`, JS в `pc/`), спрашивают сервер в фоне и блокируют по ответу, если вкладка всё ещё на той же странице. Termux-вариант на Android удаляется.

**Tech Stack:** Python 3.10+ (stdlib: `http.server`, `sqlite3`, `subprocess`, `threading`, `unittest`), systemd user service; Kotlin (`HttpURLConnection`, Robolectric); JS MV3 (`fetch`, `chrome.permissions`), Playwright smoke.

**Spec:** `docs/superpowers/specs/2026-10-14-claude-server-design.md`

## Global Constraints

- Сервер — только стандартная библиотека Python ≥ 3.10; порт по умолчанию `8787`; токен — 32 случайных байта в hex.
- `Authorization: Bearer <токен>` обязателен для всего, кроме `GET /health` и `OPTIONS`; неверный → `401`.
- CORS: `Access-Control-Allow-Origin: *`, `Access-Control-Allow-Headers: Authorization, Content-Type`, `Access-Control-Allow-Methods: GET, POST, OPTIONS`.
- Кэш: SQLite `~/.local/state/tabguard/cache.db`, срок **30 дней**, кэшируются только `block`/`allow`.
- Вызов: `claude -p --model haiku --tools "" --system-prompt "Ты классификатор. Отвечай только числом." <промпт>`, env `DISABLE_AUTOUPDATER=1`, `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1`; таймаут **60 с**; один вызов одновременно.
- Таймаут клиента — **15 с**; любая ошибка сервера → страница не блокируется, ошибка видна в настройках.
- Поисковые движки (для `kind: search`): хосты `google.*`, `yandex.*`, `ya.ru`, `bing.com`, `duckduckgo.com` (с `www.` или без), параметр запроса `q` или `text`, непустой.
- Страницы `claude.ai`, `anthropic.com`, не-http — серверу не отправляются.
- Тексты интерфейса — русские; в Android — в `strings.xml`.

## Review Focus

1. **Сервер ответил «плохо», а человек уже на другой странице** — блокировать нельзя (иначе выкинет с нормального сайта). Тесты: Task 4 smoke `late block for a page already left is ignored`; Task 6 `stale server verdict does not block` (через `TabPipeline` уже есть: проверяется в `BlockAction` по текущей вкладке).
2. **`claude` не залогинен / вернул мусор / завис** — `503`, без кэширования, без падения сервера; следующий запрос работает. Тест: Task 2 `garbage reply is 503 and not cached`, `timeout is 503`.
3. **Два устройства одновременно спрашивают одно и то же** — один вызов `claude`. Тест: Task 2 `concurrent identical requests call claude once`.
4. **Запрос без токена или с чужим токеном из Tailscale-сети** — `401`, `claude` не вызывается. Тест: Task 3 `wrong token is 401 and claude is not called`.
5. **Изменение категорий** — старые ответы кэша не применяются. Тест: Task 2 `changed categories miss the cache`.

---

## Структура файлов

```
server/
  tabguard_server.py      — HTTP: маршруты, токен, CORS, JSON; main() читает server.env
  classifier.py           — промпт, разбор ответа, ключ кэша, Classifier (очередь + склейка), run_claude
  cache.py                — Cache (SQLite, TTL)
  install.sh              — установка/обновление службы
  tabguard.service        — шаблон systemd user unit
  tests/fake_claude.py    — заглушка `claude` для тестов
  tests/test_*.py
core/src/main/kotlin/app/tabguard/core/server/ServerQuery.kt   (+ тест)
app/src/main/kotlin/app/tabguard/server/ServerClient.kt, ServerClassifier.kt (+ тесты)
pc/extension/src/core/serverQuery.js, pc/extension/src/serverClient.js (+ тесты)
pc/e2e/server.smoke.cjs, pc/e2e/fakeServer.cjs
docs/manual-test-server.md
```

Удаляются: `app/.../termux/*`, его тест, `termux/`, `docs/termux-setup.md`.

---

### Task 1: Кэш и классификация на сервере (без HTTP)

**Files:**
- Create: `server/cache.py`, `server/classifier.py`, `server/tests/fake_claude.py`
- Test: `server/tests/test_cache.py`, `server/tests/test_classifier.py`

**Interfaces:**
- Produces:
  - `Cache(path: str, now: Callable[[], float] = time.time, ttl: float = 30*86400)` с `get(key: str) -> dict | None`, `put(key: str, value: dict) -> None` (таблица `cache(key TEXT PRIMARY KEY, value TEXT, at REAL)`; каталог создаётся).
  - `build_prompt(req: dict) -> tuple[str, list[str]]` — категории нумеруются 1..n строками `N: имя (слова: a, b)`, далее `Заголовок: …`, для search `Запрос: …`, для site `Сайт: <host>` и `Адрес: …`; `нет` для пустых; поля обрезаются до 300; последняя строка — как в `ClassificationPrompt.kt`. Возвращает промпт и список `id` по порядку номеров.
  - `parse_reply(reply: str, ids: list[str]) -> dict` → `{"verdict": "allow", "categoryId": None}` для `0`, `{"verdict": "block", "categoryId": ids[n-1]}` для `1..n`; иначе `ValueError`.
  - `cache_key(req: dict) -> str` — `kind`, нормализованная цель (`host.lower()` без `www.` / `query` — `lower()`, `ё→е`, пробелы схлопнуты, trim) и `sha256` от JSON `[[id, name, keywords], …]` категорий.
  - `class Classifier(cache: Cache, run: Callable[[str], str])` с `classify(req: dict) -> dict` — кэш → (под `threading.Lock` один вызов `run`) → `parse_reply` → `put` → результат с `"cached": bool`. Одинаковый `cache_key`, пришедший во время вызова, ждёт его результата (`threading.Event` в словаре in-flight) и вызывает `run` не повторно. Исключение из `run`/`parse_reply` пробрасывается как `ClaudeError(str)`.
  - `run_claude(prompt: str, claude_bin: str = "claude", timeout: float = 60) -> str` — `subprocess.run` с аргументами и env из Global Constraints; ненулевой код или таймаут → `ClaudeError`.

`fake_claude.py`: печатает `FAKE_REPLY` (по умолчанию `0`), спит `FAKE_DELAY` секунд, дописывает строку в файл `FAKE_LOG`, если задан; выходит с `FAKE_EXIT`.

- [ ] **Step 1: Тесты** (`unittest`, временный каталог)

```python
def test_cache_expires_after_30_days(self): ...        # put at t=0; get at 29d → value; at 31d → None
def test_prompt_numbers_categories_and_maps_ids(self): # ids ['porn','x'] → '1: Порно (слова: porn, xxx)', '2: …'; returns ['porn','x']
def test_search_prompt_has_query_and_site_prompt_has_host(self): ...
def test_parse_zero_allow_number_block_garbage_error(self):
    assert parse_reply(' 0\n', ['p']) == {'verdict': 'allow', 'categoryId': None}
    assert parse_reply('Категория 1.', ['p']) == {'verdict': 'block', 'categoryId': 'p'}
    # '7' и 'не знаю' → ValueError
def test_cache_key_normalizes_target(self): # 'WWW.Example.com' == 'example.com'; 'Голые  Ёлки' == 'голые елки'
def test_changed_categories_miss_the_cache(self): ...  # другой список слов → другой ключ → run вызван снова
def test_second_identical_request_is_cached(self): ... # run вызван 1 раз, второй ответ cached=True
def test_concurrent_identical_requests_call_claude_once(self): ... # 3 потока, run спит 0.3 с → 1 вызов, 3 одинаковых ответа
def test_garbage_reply_is_error_and_not_cached(self): ... # ClaudeError; второй вызов снова вызывает run
def test_run_claude_uses_fake_binary_and_flags(self): ... # claude_bin=fake_claude.py; FAKE_LOG содержит '--model haiku' и '--tools'
def test_run_claude_timeout(self): ...                  # FAKE_DELAY=2, timeout=0.5 → ClaudeError
```

- [ ] **Step 2: Run** `cd server && python3 -m unittest discover -s tests -v` → FAIL (модули не найдены)
- [ ] **Step 3: Реализовать** `cache.py`, `classifier.py`, `fake_claude.py`
- [ ] **Step 4: Run** → PASS
- [ ] **Step 5: Commit** `git commit -m "server: cache and Claude classifier"`

---

### Task 2: HTTP-сервер

**Files:**
- Create: `server/tabguard_server.py`
- Test: `server/tests/test_http.py`

**Interfaces:**
- Consumes: Task 1
- Produces:
  - `validate_request(body: object) -> dict` — `kind` ∈ {search, site}; для search непустой `query`, для site непустой `host`; `categories` — непустой список объектов `{id: str, name: str, keywords: list[str]}`; `title`/`url` — str или None; иначе `ValueError`.
  - `make_server(host: str, port: int, token: str, classifier: Classifier, claude_version: Callable[[], str | None]) -> ThreadingHTTPServer`
  - `main()` — читает `~/.config/tabguard/server.env` (`KEY=VALUE`), создаёт `Cache` и `Classifier(cache, run_claude)`, `serve_forever()`.

Маршруты и коды — из спеки и Global Constraints; тело ответа всегда JSON (`Content-Type: application/json; charset=utf-8`).

- [ ] **Step 1: Тесты** (сервер на `127.0.0.1:0` в потоке; фейковый `run`)

```python
def test_health_without_token_has_no_details(self): # 200 {"ok": true}
def test_health_with_token_reports_claude_version(self): # {"ok": true, "claude": "2.1.288 (Claude Code)"}
def test_wrong_token_is_401_and_claude_is_not_called(self): ...
def test_classify_block(self):  # run → '1' → 200 {"verdict":"block","categoryId":"porn","cached":false}
def test_bad_body_is_400(self): # kind='x' / без categories / не JSON
def test_claude_failure_is_503(self): # run бросает → 503 {"error": "..."}
def test_options_preflight_is_204_with_cors(self): # OPTIONS без токена; заголовки CORS
def test_cors_header_on_classify(self): ...
```

- [ ] **Step 2: Run** → FAIL
- [ ] **Step 3: Реализовать**
- [ ] **Step 4: Run** `cd server && python3 -m unittest discover -s tests -v` → PASS
- [ ] **Step 5: Commit** `git commit -m "server: HTTP API with token and CORS"`

---

### Task 3: Установка и служба

**Files:**
- Create: `server/install.sh`, `server/tabguard.service`, `docs/manual-test-server.md`
- Modify: `.github/workflows/android.yml` — job `server`: `python3 -m unittest discover -s server/tests -v`

`tabguard.service` (user unit): `ExecStart=/usr/bin/python3 %h/tabguard-server/tabguard_server.py`, `Restart=always`, `RestartSec=5`, `Environment=PATH=%h/.local/bin:/usr/local/bin:/usr/bin:/bin`, `WantedBy=default.target`.

`install.sh` (bash, `set -euo pipefail`, запускается обычным пользователем из папки `server/`):
1. `python3` ≥ 3.10 и `tailscale` есть, иначе — понятная ошибка и выход.
2. Если нет `~/.local/bin/claude` и `claude` в PATH: `curl -fsSL https://claude.ai/install.sh | bash`.
3. Если `claude -p "Ответь 0"` (таймаут 60) не печатает `0` — сообщение «выполните `claude` и войдите под подпиской, затем запустите install.sh снова», выход 1.
4. Копирует `tabguard_server.py`, `classifier.py`, `cache.py` в `~/tabguard-server/`.
5. `~/.config/tabguard/server.env`: сохраняет существующий `TABGUARD_TOKEN`, иначе генерирует (`python3 -c 'import secrets;print(secrets.token_hex(32))'`); `TABGUARD_HOST=$(tailscale ip -4 | head -1)`, `TABGUARD_PORT=8787`; права `600`.
6. Юнит в `~/.config/systemd/user/`, `loginctl enable-linger "$USER"`, `systemctl --user daemon-reload && systemctl --user enable --now tabguard` (для обновления — `restart`).
7. `curl -fsS http://$HOST:8787/health` → печатает «Готово», адрес `http://$HOST:8787` и токен.

`docs/manual-test-server.md` — чек-лист: установка, `/health`, «Проверить связь» на телефоне и ПК, поиск без слов из словаря, но явно взрослый → блок через несколько секунд; обычный поиск не блокируется; повторный визит на сайт не тратит запрос (в журнале `journalctl --user -u tabguard` нет нового вызова); выключенный сервер → «Сервер недоступен».

- [ ] **Step 1: Run** `bash -n server/install.sh && systemd-analyze verify server/tabguard.service 2>&1 | grep -v 'not found' || true` → без синтаксических ошибок
- [ ] **Step 2: Run** `shellcheck server/install.sh` (если установлен) → без ошибок уровня error
- [ ] **Step 3: Commit, push**, CI job `server` зелёный

---

### Task 4: Расширение: запрос к серверу

**Files:**
- Create: `pc/extension/src/core/serverQuery.js`, `pc/extension/src/serverClient.js`, `pc/e2e/fakeServer.cjs`, `pc/e2e/server.smoke.cjs`
- Modify: `pc/extension/src/background.js`, `pc/extension/manifest.json` (`optional_host_permissions: ["http://*/*"]`), `pc/e2e/smoke.cjs` (подключить `server.smoke.cjs`; маршрутизация не трогает `http://127.0.0.1`)
- Test: `pc/test/serverQuery.test.js`, `pc/test/serverClient.test.js`

**Interfaces:**
- Consumes: `decide`, `safeSearchUrl`, `isAllowlisted`, store (`chrome.storage.local`)
- Produces:
  - `buildServerQuery(tab: {url, title}): {kind: 'search', query: string, title, url} | {kind: 'site', host: string, title, url} | null` — `null` для не-http, allowlist; для site — `null`, если заголовок пустой (ждём заголовок).
  - `classifyOnServer(settings: {url: string, token: string}, query, categories: Category[], fetchImpl = fetch): Promise<{verdict: 'block'|'allow', categoryId: string|null}>` — `POST {url}/classify`, тело = `query` + `categories: [{id, name, keywords}]` (только включённые), `AbortSignal.timeout(15000)`; не-200 → `Error('HTTP <код>')`.
  - `checkServerHealth(settings, fetchImpl = fetch): Promise<string>` — `GET /health` с токеном → строка версии Claude; ошибка → `Error`.
  - `chrome.storage.local`: `server: {enabled: boolean, url: string, token: string}`, `serverStatus: {ok: boolean, message: string, at: number}`.

`background.js`: после `decide` = null и отсутствия переписывания безопасного поиска — если `server.enabled`: `buildServerQuery` → (кэш в памяти по `kind|query/host`; in-flight склейка) → `classifyOnServer` → при `block` и если `(await chrome.tabs.get(tabId)).url === url` → тот же путь блокировки, что и локальный (редирект + буфер + счётчик, категория по `categoryId`). Ошибки → `serverStatus` с `ok: false`.

- [ ] **Step 1: Юнит-тесты**

```js
test('google and yandex searches become search queries', ...);   // q=голые → {kind:'search', query:'голые'}; yandex text=
test('other pages become site queries with host', ...);           // https://www.Example.com/a → host 'example.com'
test('site query waits for a title', ...);                        // title null → null
test('claude, browser pages and empty searches are not sent', ...);
test('classifyOnServer posts query, enabled categories and token', ...); // fake fetch: проверить URL, Authorization, тело
test('classifyOnServer rejects on non-200', ...);
```

- [ ] **Step 2: Smoke-сценарии** в `server.smoke.cjs` (поддельный сервер на `127.0.0.1:<порт>`, `fakeServer.cjs` отвечает `block` для запросов со словом `tabooword`, считает вызовы):
  - `server verdict blocks a search no keyword catches` — `google.com/search?q=tabooword` → Claude
  - `server allow leaves the page` — `q=ordinary` → не Claude за 3 с
  - `late block for a page already left is ignored` — сервер отвечает через 2 с; страница за это время уходит на `example.com/other` → остаётся там
  - `unreachable server does not block and reports status` — порт закрыт → `serverStatus.ok === false`
  - `wrong token reports 401` — `serverStatus.message` содержит `401`
  (настройки задаются через `chrome.storage.local.set` из воркера)
- [ ] **Step 3: Run** `cd pc && npm test` и smoke → FAIL
- [ ] **Step 4: Реализовать**
- [ ] **Step 5: Run** → PASS (smoke 3 раза подряд без сбоев)
- [ ] **Step 6: Commit** `git commit -m "pc: ask the home server for pages the local check passes"`

---

### Task 5: Расширение: настройки сервера

**Files:**
- Modify: `pc/extension/options/options.html`, `options.js`, `options.css`, `pc/e2e/server.smoke.cjs`

Блок «Проверка через сервер» над категориями: переключатель, поля «Адрес сервера» (`http://100.x.x.x:8787`) и «Токен» (type=password), кнопки «Сохранить» и «Проверить связь», строка статуса (`serverStatus` или результат проверки: «OK, Claude <версия>» / «Сервер недоступен: <ошибка>»). «Сохранить»: адрес без завершающего `/`, `chrome.permissions.request({origins: [origin + '/*']})` (отказ не мешает сохранить — см. спеку), запись `server`.

- [ ] **Step 1: Smoke** `options save server settings and test connection` — заполнить поля адресом поддельного сервера, «Сохранить» (в тесте `chrome.permissions.request` может вернуть false — сохранение всё равно происходит), «Проверить связь» → текст `OK, Claude`
- [ ] **Step 2: Run** smoke → FAIL
- [ ] **Step 3: Реализовать**
- [ ] **Step 4: Run** smoke → PASS
- [ ] **Step 5: Commit** `git commit -m "pc: server settings in the options page"`

---

### Task 6: Android: клиент сервера вместо Termux

**Files:**
- Create: `core/src/main/kotlin/app/tabguard/core/server/ServerQuery.kt`, `app/src/main/kotlin/app/tabguard/server/ServerClient.kt`, `app/src/main/kotlin/app/tabguard/server/ServerClassifier.kt`
- Modify: `SettingsStore.kt` (+ `serverEnabled: Flow<Boolean>`, `serverUrl: Flow<String>`, `serverToken: Flow<String>` и сеттеры; `claudeEnabled` → удалить), `AppContainer.kt` (`server = ServerClient()`), `TabGuardAccessibilityService.kt` (`ClassifierChain({ if (serverEnabled) ServerClassifier(client, settingsSnapshot) else null })`), `MainViewModel.kt`, `SetupCard.kt`, `MainActivity.kt`, `strings.xml`, `AndroidManifest.xml` (удалить Termux-разрешение и receiver; добавить `INTERNET` и `android:usesCleartextTraffic="true"` — сервер по http внутри Tailscale)
- Delete: `app/.../termux/*`, `app/src/test/.../termux/*`, `termux/`, `docs/termux-setup.md`
- Test: `core/src/test/.../server/ServerQueryTest.kt`, `app/src/test/.../server/ServerClientTest.kt`, `MainViewModelTest.kt` (обновить)

**Interfaces:**
- Consumes: `Classifier`, `Verdict`, `TabSnapshot`, `Category` (`:core`)
- Produces:
  - `sealed interface ServerQuery { data class Search(val query: String); data class Site(val host: String) }` и `object ServerQueries { fun of(tab: TabSnapshot): ServerQuery? }` — правила из Global Constraints; если `extractHost(tab.url) == null` и `tab.url` не пустой — `Search(tab.url.trim())`; allowlist → `null`.
  - `class ServerClient(timeoutMs: Int = 15_000) { fun classify(baseUrl: String, token: String, query: ServerQuery, tab: TabSnapshot, categories: List<Category>): Verdict; fun health(baseUrl: String, token: String): String }` — блокирующие, вызываются на `Dispatchers.IO`; JSON через `org.json`; ответ `block` → `Verdict.Block(categoryId.toLong(), VerdictSource.CLAUDE)`, `allow` → `Verdict.Allow`; ошибка → `Verdict.Unknown("сервер: <причина>")` (`health` — исключение).
  - `class ServerClassifier(client: ServerClient, settings: () -> Pair<String, String>) : Classifier`

- [ ] **Step 1: Тесты**

```kotlin
// ServerQueryTest (core, локально)
@Test fun `google search url becomes search query`()          // "google.com/search?q=голые" → Search("голые")
@Test fun `omnibox text without host becomes search query`()  // url "голые девушки" → Search("голые девушки")
@Test fun `ordinary site becomes site query`()                // "www.example.com/a" → Site("example.com")
@Test fun `claude page is not sent`()                         // "claude.ai/new?q=x" → null
// ServerClientTest (Robolectric, java.net.ServerSocket в потоке отвечает заданным HTTP)
@Test fun `block response maps to Block CLAUDE`()
@Test fun `request has bearer token and json body`()
@Test fun `401 maps to Unknown with code`()
@Test fun `unreachable server maps to Unknown`()
@Test fun `health returns claude version`()
// MainViewModelTest
@Test fun `testServer reports health result`()
```

- [ ] **Step 2: Run** `./gradlew :core:test` → FAIL (локально); `:app` — в CI
- [ ] **Step 3: Реализовать**; блок «Проверка через сервер» в `SetupCard`: переключатель, поля адреса и токена, «Проверить связь», статус (`lastClaudeError` → «Сервер недоступен: …»)
- [ ] **Step 4: Run** `./gradlew :core:test` → PASS; push → CI `build` зелёный
- [ ] **Step 5: Commit** `git commit -m "app: home server classifier replaces Termux"`
