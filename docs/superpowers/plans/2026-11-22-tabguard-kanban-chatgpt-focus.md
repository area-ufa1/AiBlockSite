# TabGuard × Kanban × ChatGPT: фокус после блокировки — план реализации

> **Для исполнителей:** выполняй этот план через `executing-plans`. До отдельной команды на реализацию сохраняй режим только планирования. Обязательны тесты, независимое ревью, проверка пользовательского сценария и отдельная граница публикации.

**Цель:** после блокировки TabGuard получить контекст выбранной доски Kanban, открыть веб-ChatGPT с профессиональным промптом о трёх выполнимых вариантах на 10–15 минут, автоматически вставить и отправить сообщение, а затем дать пользователю управляемый способ связать выбранный результат с карточкой Kanban.

**Результат первой рабочей версии:** TabGuard подключается к Kanban с ограниченным токеном, читает задачи из выбранной доски и колонки, передаёт их в ChatGPT и отправляет промпт автоматически. При недоступности Kanban или ChatGPT сохраняется общий сценарий и промпт в буфере обмена.

**Архитектура:** TabGuard остаётся инициатором сценария. Kanban предоставляет отдельный интеграционный API с токенами и областями доступа. ChatGPT получает текст через content script после загрузки страницы. Запись в Kanban выполняется отдельным подтверждаемым действием после разбора ответа.

**Стек:** Chrome Manifest V3, service worker, content scripts, `chrome.storage.local`, `chrome.storage.session`, React/Vite Kanban, FastAPI, SQLAlchemy, PostgreSQL, JWT Kanban auth, Playwright, Node test runner и pytest.

**Основание:** текущие `pc/extension/src/background.js`, `pc/extension/src/core/prompt.js`, `pc/extension/src/core/decide.js`, YouTube content scripts, Kanban API-клиент, карточки и существующая система AI-чеклистов.

## Границы и решения

- [ ] Обозначить веб-ChatGPT целевым интерфейсом по адресу `chatgpt.com`. Подключение к отдельному приложению Codex потребует отдельного deep-link и не входит в этот поток.
- [ ] Использовать Kanban как источник существующих задач. ChatGPT предлагает следующий шаг, но не становится источником истины для карточек.
- [ ] Сделать чтение Kanban обязательной частью интеграции только при успешном подключении. При ошибке сети, авторизации или конфигурации продолжать сценарий с общим промптом.
- [ ] Передавать в ChatGPT только выбранные поля карточек. Не передавать токены, комментарии, вложения и историю чатов.
- [ ] Ограничить контекст десятью карточками, названием до 200 символов и описанием до 600 символов на карточку.
- [ ] Разделить области доступа интеграционного токена на `read_context`, `create_card` и `move_card`.
- [ ] В первой версии включить только `read_context`. Запись в Kanban выполнять после отдельного подтверждения пользователя.
- [ ] Сохранить текущую отправку промпта в буфер обмена как fallback.
- [ ] Автоматическую отправку в ChatGPT выполнять ровно один раз на одно событие блокировки, используя одноразовый nonce.
- [ ] Не создавать и не перемещать карточки только на основании текста ответа ChatGPT без подтверждения пользователя.

## Рабочий поток A. Интеграция Kanban

### Task 1: Зафиксировать контракт интеграции

**Files:**
- Create: `Kanban/docs/tabguard-integration.md`
- Modify: `Kanban/README.md` в разделе локальных интеграций
- Test: `Kanban/backend/tests/test_tabguard_contract.py`

- [ ] Описать URL API и формат интеграционного токена.
- [ ] Описать области доступа и срок действия токена.
- [ ] Зафиксировать формат контекста, который получает TabGuard.
- [ ] Зафиксировать поведение при пустой доске, недоступной колонке, истёкшем токене и сетевой ошибке.
- [ ] Зафиксировать, что пользовательские карточки не создаются автоматически.
- [ ] Добавить контрактные примеры JSON для успешного ответа и ошибок 401, 403, 404, 429 и 503.

### Task 2: Добавить интеграционные токены Kanban

**Files:**
- Create: `Kanban/backend/models.py` модель `TabGuardIntegrationToken`
- Create: `Kanban/alembic/versions/<revision>_add_tabguard_tokens.py`
- Modify: `Kanban/backend/schemas.py`
- Create: `Kanban/backend/services/tabguard.py`
- Test: `Kanban/backend/tests/test_tabguard_tokens.py`

**Interfaces:**
- `POST /api/integrations/tabguard/tokens`
- `GET /api/integrations/tabguard/tokens`
- `DELETE /api/integrations/tabguard/tokens/{token_id}`

- [ ] Генерировать токен только один раз и хранить в базе только его хэш.
- [ ] Показывать пользователю полный токен только в ответе на создание.
- [ ] Привязать токен к пользователю, рабочему пространству и выбранной области доступа.
- [ ] Поддержать отзыв токена без отзыва пользовательского JWT.
- [ ] Добавить `last_used_at`, `created_at`, `revoked_at`, название устройства и срок действия.
- [ ] Проверять область доступа до каждого чтения и действия.
- [ ] Не писать значение токена в логи, события, ошибки и ответы API после создания.
- [ ] Покрыть повторный отзыв, просроченный токен, неверный хэш и отсутствие области доступа.

### Task 3: Добавить API контекста фокуса

**Files:**
- Create: `Kanban/backend/routers/tabguard.py`
- Modify: `Kanban/backend/main.py`
- Modify: `Kanban/backend/dependencies.py`
- Modify: `Kanban/backend/config.py`
- Test: `Kanban/backend/tests/test_tabguard_context.py`

**Interface:**

```text
GET /api/integrations/tabguard/context
Authorization: TabGuard <integration-token>
Query: board_id, column_id, limit=10
```

```json
{
  "board": {"id": 2, "title": "Работа"},
  "column": {"id": 7, "title": "В работе"},
  "cards": [
    {
      "id": 123,
      "title": "Короткое название",
      "description": "Ограниченный текст описания",
      "priority": "medium",
      "deadline": null,
      "column_id": 7
    }
  ]
}
```

- [ ] Разрешать только доски и колонки, доступные владельцу токена.
- [ ] Возвращать карточки в стабильном порядке по позиции Kanban.
- [ ] Ограничивать `limit` диапазоном 1–10 на сервере.
- [ ] Нормализовать пустые описания и обрезать длинные поля на сервере.
- [ ] Не возвращать assignee-данные, комментарии, вложения, внутренние журналы и JWT.
- [ ] Возвращать понятные ошибки с request ID.
- [ ] Добавить rate limit на интеграционный токен.
- [ ] Проверить CORS и вызов из service worker с локальным и удалённым URL Kanban.

### Task 4: Добавить управляемую запись результата

**Files:**
- Modify: `Kanban/backend/routers/tabguard.py`
- Modify: `Kanban/backend/schemas.py`
- Modify: `Kanban/backend/services/tabguard.py`
- Test: `Kanban/backend/tests/test_tabguard_actions.py`

**Interfaces:**

```text
POST /api/integrations/tabguard/focus-sessions
Authorization: TabGuard <integration-token>
```

```json
{
  "board_id": 2,
  "column_id": 7,
  "existing_card_id": 123,
  "title": "Первый шаг",
  "description": "Контекст и критерий готовности",
  "source": "tabguard",
  "source_url": "https://www.youtube.com/watch?v=..."
}
```

- [ ] Разрешать запись только при наличии `create_card` или `move_card`.
- [ ] Для существующей карточки поддержать добавление короткой заметки или перемещение выбранной карточки.
- [ ] Для новой карточки использовать явно выбранную колонку и лимит длины полей.
- [ ] Защитить повторную отправку через `client_request_id` и уникальность в пределах токена.
- [ ] Создавать аудит события с источником `tabguard`.
- [ ] Не вызывать запись автоматически после ответа модели.
- [ ] Добавить отдельные тесты для отказа без подтверждения, повторного запроса и неверной доски.

### Task 5: Добавить настройки интеграции в Kanban

**Files:**
- Create: `Kanban/frontend/src/api/integrations.js`
- Create: `Kanban/frontend/src/pages/SettingsPage/tabs/TabGuardTab.jsx`
- Create: `Kanban/frontend/src/pages/SettingsPage/tabs/TabGuardTab.module.css`
- Modify: `Kanban/frontend/src/pages/SettingsPage/SettingsPage.jsx`
- Test: `Kanban/frontend/src/pages/SettingsPage/tabs/TabGuardTab.test.jsx`

- [ ] Добавить раздел «TabGuard» в настройки пользователя или рабочего пространства.
- [ ] Дать возможность создать токен с выбранными областями доступа.
- [ ] Показать токен только один раз и дать кнопку копирования.
- [ ] Показать список активных токенов с датой создания, последнего использования и отзывом.
- [ ] Дать выбор доски и колонки по умолчанию для контекста.
- [ ] Отдельно показывать разрешения чтения и записи.
- [ ] Показать понятное предупреждение о том, какие данные могут попасть в ChatGPT.
- [ ] Проверить состояние загрузки, ошибку API, отзыв и повторное создание токена.

## Рабочий поток B. TabGuard и контекст Kanban

### Task 6: Добавить хранилище и настройки Kanban в TabGuard

**Files:**
- Create: `TabGuard-PC/pc/extension/src/kanban/storage.js`
- Create: `TabGuard-PC/pc/extension/src/kanban/client.js`
- Modify: `TabGuard-PC/pc/extension/options/options.html`
- Modify: `TabGuard-PC/pc/extension/options/options.js`
- Modify: `TabGuard-PC/pc/extension/options/options.css`
- Modify: `TabGuard-PC/pc/extension/manifest.json`
- Test: `TabGuard-PC/pc/test/kanban-client.test.js`

**Stored settings:**

```js
{
  enabled: false,
  baseUrl: "http://localhost:8000",
  token: "stored locally",
  boardId: null,
  columnId: null,
  maxCards: 10,
  includeDescriptions: true,
  autoSendChatGPT: true,
  clipboardFallback: true
}
```

- [ ] Добавить форму адреса Kanban, интеграционного токена, доски и колонки.
- [ ] Получать список досок и колонок только после успешной проверки токена.
- [ ] Запрашивать `optional_host_permissions` для введённого origin Kanban.
- [ ] Отдельно хранить токен и настройки в `chrome.storage.local`.
- [ ] Хранить одноразовые handoff-состояния в `chrome.storage.session`.
- [ ] Не выводить токен в DOM, консоль, URL, промпт и статистику.
- [ ] Добавить кнопку «Проверить подключение» с проверкой `/health` и контекста.
- [ ] Обрабатывать 401 удалением только интеграционного токена и понятным сообщением в настройках.
- [ ] При недоступности Kanban возвращать `null`-контекст без блокировки основного сценария.

### Task 7: Реализовать получение и нормализацию контекста

**Files:**
- Create: `TabGuard-PC/pc/extension/src/kanban/context.js`
- Modify: `TabGuard-PC/pc/extension/src/background.js`
- Test: `TabGuard-PC/pc/test/kanban-context.test.js`

- [ ] Добавить `getFocusContext(settings)` с тайм-аутом 3 секунды.
- [ ] Валидировать типы и длины полей ответа Kanban до передачи в промпт.
- [ ] Удалять пустые, повторяющиеся и повреждённые карточки.
- [ ] Сохранять порядок карточек Kanban.
- [ ] Ограничивать итоговый контекст десятью карточками.
- [ ] Не блокировать страницу дольше тайм-аута Kanban-запроса.
- [ ] Покрыть успешный ответ, 401, 403, 404, timeout, malformed JSON и пустую доску.

## Рабочий поток C. Профессиональный промпт

### Task 8: Выделить генератор промпта

**Files:**
- Modify: `TabGuard-PC/pc/extension/src/core/prompt.js`
- Modify: `TabGuard-PC/pc/extension/src/core/decide.js`
- Test: `TabGuard-PC/pc/test/prompt.test.js`
- Test: `TabGuard-PC/pc/test/decide.test.js`

**Interface:**

```js
buildSupportPrompt(tab, categoryName, kind, {
  kanbanContext,
  focusMinutes: 10,
  maxOptions: 3,
})
```

- [ ] Сохранить отдельные режимы для YouTube и игровых/взрослых категорий.
- [ ] Для YouTube передавать название и ссылку, а также предупреждать ChatGPT не выдумывать содержание при недоступной ссылке.
- [ ] Просить ровно три небольших варианта.
- [ ] Для каждого варианта требовать название, первый шаг, критерий готовности и длительность.
- [ ] Ограничить первый шаг двумя минутами и общую работу диапазоном 10–15 минут.
- [ ] Предпочитать существующие карточки Kanban, когда контекст доступен.
- [ ] Просить выбрать один вариант для состояния «трудно начать».
- [ ] Использовать спокойный тон без стыда, давления, морализаторства и длинной лекции.
- [ ] Завершать одним конкретным призывом к действию.
- [ ] Добавить компактный структурированный блок для будущего разбора результата ChatGPT.
- [ ] Не включать секреты, внутренние JWT, полные URL API и приватные комментарии Kanban.

### Task 9: Обновить redirect URL и защиту от повторов

**Files:**
- Modify: `TabGuard-PC/pc/extension/src/core/prompt.js`
- Modify: `TabGuard-PC/pc/extension/src/background.js`
- Test: `TabGuard-PC/pc/test/handoff-url.test.js`

- [ ] Добавить `handoff_id` с криптографически случайным nonce.
- [ ] Передавать промпт в `q` и nonce в отдельном параметре `tabguard_handoff`.
- [ ] Ограничить длину URL и оставить полный промпт в `chrome.storage.session` по nonce.
- [ ] В URL не передавать токен Kanban.
- [ ] Привязать handoff к исходной вкладке и времени жизни не более 10 минут.
- [ ] Повторное открытие того же URL не должно повторно отправлять сообщение.
- [ ] Сохранять fallback-поведение с буфером обмена.

## Рабочий поток D. Автоматическая вставка и отправка в ChatGPT

### Task 10: Добавить content script ChatGPT

**Files:**
- Create: `TabGuard-PC/pc/extension/content/chatgpt-submit.js`
- Create: `TabGuard-PC/pc/extension/src/core/chatgpt-handoff.js`
- Modify: `TabGuard-PC/pc/extension/manifest.json`
- Test: `TabGuard-PC/pc/test/chatgpt-handoff.test.js`

- [ ] Подключить script к `https://chatgpt.com/*` и `https://chat.openai.com/*`.
- [ ] Читать только одноразовый `tabguard_handoff` и получать промпт через `chrome.runtime.sendMessage`.
- [ ] Использовать `MutationObserver` и ограниченный retry до 20 секунд.
- [ ] Поддержать `textarea` и `contenteditable` поле ChatGPT.
- [ ] Устанавливать значение через нативный setter и событие `input`, чтобы обновить состояние React.
- [ ] Находить кнопку отправки несколькими проверяемыми селекторами.
- [ ] Ждать, пока кнопка станет доступной и не будет состояния генерации.
- [ ] Отправлять запрос ровно один раз.
- [ ] Записывать nonce в `sessionStorage` после успешного запуска отправки.
- [ ] При отсутствии поля, авторизации или кнопки завершать попытки с сохранённым clipboard fallback.
- [ ] Не читать и не отправлять произвольные сообщения пользователя.

### Task 11: Добавить runtime handoff-состояния и диагностику

**Files:**
- Modify: `TabGuard-PC/pc/extension/src/background.js`
- Modify: `TabGuard-PC/pc/extension/src/offscreen.js`
- Create: `TabGuard-PC/pc/extension/src/handoff-status.js`
- Test: `TabGuard-PC/pc/test/background-handoff.test.js`

- [ ] Обработать сообщения `handoff_ready`, `handoff_sent`, `handoff_failed` и `handoff_skipped`.
- [ ] Удалять session payload после успешной отправки или истечения TTL.
- [ ] Не считать переход на `chatgpt.com` доказательством отправки.
- [ ] Разделять статусы «промпт сформирован», «ChatGPT загрузился», «сообщение отправлено» и «fallback в буфер».
- [ ] Не допускать повторного `chrome.tabs.update` из title/status событий уже обработанного handoff.
- [ ] Добавить ограниченные диагностические сообщения без содержимого промпта и без URL с query.

### Task 12: Проверить браузерный сценарий автоотправки

**Files:**
- Create: `TabGuard-PC/pc/e2e/chatgpt-handoff.smoke.cjs`
- Modify: `TabGuard-PC/pc/e2e/smoke.cjs` при необходимости
- Create: `TabGuard-PC/pc/e2e/fixtures/chatgpt-mock.html`

- [ ] На mock-странице проверить появление поля после задержки.
- [ ] Проверить вставку Unicode, переносов строк, кавычек и ссылки.
- [ ] Проверить ровно один submit.
- [ ] Проверить refresh/back и защиту от повторной отправки.
- [ ] Проверить отсутствие submit при пустом или просроченном handoff.
- [ ] Отдельно провести ручную проверку на авторизованном ChatGPT с реальным DOM.
- [ ] Проверить сценарии logout, медленная сеть, блокировка отправки и изменение селекторов.

## Рабочий поток E. Разбор ответа и подтверждаемая связь с Kanban

### Task 13: Формализовать ответ ChatGPT

**Files:**
- Modify: `TabGuard-PC/pc/extension/src/core/prompt.js`
- Create: `TabGuard-PC/pc/extension/src/core/focus-options.js`
- Test: `TabGuard-PC/pc/test/focus-options.test.js`

- [ ] Попросить ChatGPT дополнительно выводить ограниченный JSON-блок с тремя вариантами.
- [ ] У каждого варианта поддержать `existing_card_id`, `title`, `first_step`, `done_when`, `minutes`.
- [ ] Парсер должен принимать только валидный JSON внутри специального маркера.
- [ ] Игнорировать неподтверждённые ID и любые поля, которых нет в схеме.
- [ ] Ограничить число вариантов тремя и длину строк.
- [ ] Сохранять обычный читаемый ответ для пользователя, даже если JSON-блок повреждён.

### Task 14: Добавить экран подтверждения действия

**Files:**
- Modify: `TabGuard-PC/pc/extension/options/options.html`
- Modify: `TabGuard-PC/pc/extension/options/options.js`
- Modify: `TabGuard-PC/pc/extension/options/options.css`
- Create: `TabGuard-PC/pc/extension/src/core/focus-action.js`
- Test: `TabGuard-PC/pc/test/focus-action.test.js`

- [ ] Показать три распознанных варианта с источником карточки Kanban.
- [ ] Дать действия «Открыть карточку», «Создать карточку», «Переместить карточку» и «Ничего не менять».
- [ ] Показывать целевую доску и колонку перед записью.
- [ ] Требовать явное подтверждение перед любой записью.
- [ ] Поддержать отказ, повтор, истёкший токен и конфликт версии карточки.
- [ ] Не создавать карточку автоматически на основании одного ответа модели.

### Task 15: Подключить write-back в Kanban

**Files:**
- Modify: `TabGuard-PC/pc/extension/src/kanban/client.js`
- Modify: `Kanban/backend/routers/tabguard.py`
- Test: `TabGuard-PC/pc/test/kanban-actions.test.js`
- Test: `Kanban/backend/tests/test_tabguard_actions.py`

- [ ] Отправлять `client_request_id` для идемпотентности.
- [ ] Передавать только выбранный пользователем вариант.
- [ ] Для перемещения использовать текущую версию карточки и существующую optimistic-lock защиту.
- [ ] После успешной записи обновлять состояние и показывать ссылку на карточку.
- [ ] После конфликта предлагать перечитать карточку и повторить действие вручную.
- [ ] Проверить, что отзыв токена немедленно прекращает записи.

## Рабочий поток F. Тестирование, ревью и выпуск

### Task 16: Модульные и контрактные проверки

**Files:**
- Modify: `TabGuard-PC/pc/test/*.test.js` только затронутые наборы
- Create: `Kanban/backend/tests/test_tabguard_integration.py`
- Create: `Kanban/frontend/src/pages/SettingsPage/tabs/TabGuardTab.test.jsx`

- [ ] TabGuard prompt tests.
- [ ] TabGuard nonce, TTL и deduplication tests.
- [ ] TabGuard context validation tests.
- [ ] ChatGPT DOM handoff tests on mock page.
- [ ] Kanban token hash, scope, revocation and audit tests.
- [ ] Kanban context authorization and field filtering tests.
- [ ] Kanban write-back idempotency and optimistic-lock tests.
- [ ] Frontend settings tests for token creation, copy, revoke and board selection.

### Task 17: Независимое ревью

**Review focus:**

- [ ] Утечка JWT, integration token или приватных карточек в URL, логах, clipboard, Sentry и ChatGPT prompt.
- [ ] Повторная отправка промпта при `tabs.onUpdated`, Back, reload и SPA-навигации.
- [ ] Запись в Kanban без явного подтверждения и без нужной scope.
- [ ] Устаревшая версия карточки при перемещении.
- [ ] Сбой Kanban, ChatGPT logout, timeout, captcha, rate limit и изменение DOM.
- [ ] CORS и optional host permissions для локального и удалённого Kanban.
- [ ] Доступ владельца к доске, колонке и карточкам через интеграционный токен.

### Task 18: Полная верификация пользовательского сценария

**Commands:**

```text
cd C:\Users\32165\Downloads\TabGuard-PC\pc
npm test
```

```text
cd C:\Users\32165\Desktop\Kanban
pytest -q
```

- [ ] Проверить обычный заблокированный сайт.
- [ ] Проверить YouTube title, URL и жанр.
- [ ] Проверить три варианта задач с Kanban-контекстом.
- [ ] Проверить авто-вставку и авто-отправку в mock ChatGPT.
- [ ] Проверить реальный авторизованный ChatGPT вручную.
- [ ] Проверить fallback в буфер при недоступном ChatGPT.
- [ ] Проверить Kanban offline и expired token.
- [ ] Проверить явное подтверждение создания и перемещения карточки.
- [ ] Проверить свежую карточку, конфликт версии и повторный request ID.
- [ ] Проверить отсутствие секретов в логах и URL.

### Task 19: Подготовить публикацию

**Files:**
- Modify: `TabGuard-PC/docs/manual-test-pc.md`
- Modify: `Kanban/README.md`
- Modify: `Kanban/.env.example`
- Modify: `TabGuard-PC/pc/scripts/package.sh` при необходимости

- [ ] Описать настройки Kanban, scopes, pair/token flow и отзыв токена.
- [ ] Описать ручную проверку ChatGPT auto-submit и fallback.
- [ ] Обновить пример CORS и optional permissions без wildcard-доступа.
- [ ] Обновить release checklist для PC-архива и Kanban backend/frontend.
- [ ] Отдельно зафиксировать подтверждённое поведение и ограничения UI-автоматизации ChatGPT.
- [ ] Создать отдельные commits в TabGuard и Kanban.
- [ ] Перед push проверить оба worktree, тесты, `git diff --check`, секреты и состав файлов.
- [ ] Push, merge, deployment и Kanban Done выполнять только отдельными явными командами.

## Порядок выполнения

1. Task 1 и Task 2 в Kanban
2. Task 3 и Task 5 в Kanban
3. Task 6 и Task 7 в TabGuard
4. Task 8 и Task 9 в TabGuard
5. Task 10, Task 11 и Task 12 в TabGuard
6. Task 13 и Task 14 в TabGuard
7. Task 4 и Task 15 после подтверждения write-back scope
8. Task 16 и Task 17
9. Task 18
10. Task 19 и отдельный release workflow

## Критерии готовности

- TabGuard получает контекст из выбранной доски Kanban через ограниченный отзывный токен.
- Промпт содержит название и ссылку текущего отвлечения, три небольших варианта и конкретный первый шаг.
- ChatGPT получает промпт автоматически и отправляет его один раз после загрузки поля.
- Clipboard fallback работает при каждом отказе автоматической отправки.
- Kanban write-back не выполняется без явного подтверждения.
- Тесты подтверждают токены, scopes, приватность, idempotency, DOM handoff и optimistic locking.
- Реальный ручной сценарий подтверждён в авторизованном ChatGPT и локальном Kanban.
- Ветка каждого репозитория проходит отдельное ревью до публикации.
