# Защита от отключения, YouTube и версия для друга — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Категория «YouTube: развлечения», защита от ослабления (пауза или PIN напарника) на Android и в расширении, мастер первой настройки, публичные релизы и страница-инструкция для друга.

**Architecture:** Правила категорий расширяются двумя полями (`urlPrefixes`, `onlyHosts`) в обоих ядрах (`:core` Kotlin, `pc/extension/src/core` JS). Защита — чистый модуль состояния (`lock`) в обоих ядрах; UI/хранилище спрашивают его перед любым «ослаблением». Android дополнительно распознаёт защищённые системные экраны по дереву узлов (`ProtectedScreens` в `:core`). Релизы и сайт — GitHub Actions.

**Tech Stack:** Kotlin/Room (миграция 1→2)/Compose/Robolectric; JS MV3 + `node --test` + Playwright; GitHub Actions (`softprops/action-gh-release@v2`, `actions/deploy-pages@v4`); `git filter-repo`.

**Spec:** `docs/superpowers/specs/2026-10-24-friend-youtube-lock-design.md`

## Global Constraints

- Пауза по умолчанию **20 мин**, выбор **5–60**; окно изменений **5 мин**; PIN **4–8 цифр**, хранится `sha256(соль + PIN)` (соль 16 байт hex); **5** неверных подряд → ввод заблокирован **30 мин**.
- Ослабление = выключить/удалить категорию; сохранить категорию с меньшим набором `keywords`/`domains`/`urlPrefixes`/`onlyHosts`; сменить режим/паузу/PIN. Всё остальное свободно.
- Категория YouTube: id `youtube-fun` (Android — отдельная строка с `bundledList = "youtube-fun"`), имя «YouTube: развлечения», `urlPrefixes = ["youtube.com/shorts/"]`, `onlyHosts = ["youtube.com", "youtu.be"]`, по умолчанию **выключена**.
- `urlPrefixes` сравниваются с адресом без схемы и без ведущих `www.`/`m.`; `onlyHosts` ограничивает только слова (свои + встроенные), домены и префиксы работают везде.
- Встроенные словари — `app/src/main/assets/blocklists/<bundledList>-keywords.txt`, общие для обеих платформ.
- Интерфейс и промпты — русские.
- Перезапись истории git — **только после явного подтверждения человека в чате** (шаг Task 6).

## Review Focus

1. **Перехват настроек Android срабатывает на чужих экранах** (список приложений, уведомления, поиск в настройках с «Страж») — человек не может пользоваться телефоном. Тест: Task 5 `app list mentioning the name is not protected`, `settings search result is not protected`.
2. **Обновление у человека затирает его категории** при миграции/засеве YouTube. Тест: Task 2 `migration keeps categories and adds disabled youtube`; Task 3 `existing install gains youtube category disabled`.
3. **Окно не открывается после паузы, если расширение/приложение было закрыто** во время отсчёта. Тест: Task 3/4 `countdown survives restart` (состояние из хранилища, `now` позже).
4. **Слово YouTube-словаря блокирует обычный сайт** («реакция» в новостях). Тест: Task 1 `youtube words apply only on youtube`.
5. **Человек сам себя заблокировал PIN-ом** (5 ошибок) — ввод снова работает через 30 мин, отсчёт не вечный. Тест: Task 3 `pin lockout ends after 30 minutes`.

---

### Task 1: Правила `urlPrefixes`/`onlyHosts` и словарь YouTube (JS)

**Files:**
- Create: `app/src/main/assets/blocklists/youtube-fun-keywords.txt`
- Modify: `pc/extension/src/core/matcher.js`, `categories.js` (DEFAULT_CATEGORIES + `parseUrlPrefixes`), `prompt.js` (`buildSupportPrompt(tab, category)` — выбор текста по `category.id`/`bundledList`), `decide.js`, `pc/scripts/package.sh` (копировать все `blocklists/*-keywords.txt`), `pc/extension/src/background.js` (передать категорию в промпт)
- Test: `pc/test/youtube.test.js`, обновить `prompt.test.js`, `categories.test.js`

**Interfaces:**
- Produces: `Category += {urlPrefixes: string[], onlyHosts: string[]}` (отсутствие поля = `[]`); `matchTab` возвращает `source: 'prefix'` для префикса (проверяется после доменов, до слов); `DEFAULT_CATEGORIES` = [porn, youtube-fun]; `parseUrlPrefixes(text): string[]` (нормализация: без схемы, `www.`/`m.`, lower); промпт YouTube: «Я только что чуть не залип в развлекательный YouTube: «…». … помоги вернуться к делу и справиться с тягой к бесконечной ленте прямо сейчас: предложи пару конкретных действий на ближайшие 10 минут.»

- [ ] **Step 1: Тесты**

```js
test('shorts url is blocked before load', ...)            // https://www.youtube.com/shorts/abc → youtube-fun, source 'prefix'
test('mobile youtube shorts too', ...)                     // https://m.youtube.com/shorts/x
test('entertainment titles on youtube are blocked', ...)   // 'Лучшие приколы 2026', 'Реакция на мемы', 'Funny fails compilation', 'Пранк над другом'
test('educational titles pass', ...)                       // 'Лекция по физике', 'Урок английского', 'Как починить кран', 'Documentary: Ocean'
test('youtube words apply only on youtube', ...)           // news.ru 'Реакция рынка на ставку' → null (youtube-fun enabled)
test('youtube home and search are not blocked', ...)       // youtube.com/, youtube.com/results?search_query=физика (title 'физика - YouTube')
test('youtube category is disabled by default', ...)
test('youtube block uses its own prompt', ...)
```

- [ ] **Step 2: Run** `cd pc && npm test` → FAIL
- [ ] **Step 3: Реализовать**; словарь — из спеки (раздел 2) + стемы (`смешн`, `угар`, `ржак`, `прикол`), фразы (`топ 10`, `try not to laugh`, `лучшие моменты`)
- [ ] **Step 4: Run** → PASS; smoke (`NODE_PATH=/opt/node22/lib/node_modules node pc/e2e/smoke.cjs`) → PASS + новый сценарий `youtube shorts go to claude` (включить категорию через storage)
- [ ] **Step 5: Commit** `git commit -m "youtube category: shorts prefix and entertainment titles (PC)"`

---

### Task 2: То же в Android (`:core` + Room)

**Files:**
- Modify: `core/.../model/Models.kt` (`Category` + `urlPrefixes: List<String> = emptyList()`, `onlyHosts: Set<String> = emptySet()`; `VerdictSource.PREFIX`), `core/.../match/LocalMatcher.kt`, `core/.../support/SupportPrompt.kt` (`build(tab, categoryName, kind: PromptKind)`; `enum PromptKind { ADULT, YOUTUBE }`), `app/.../data/Entities.kt` (+ колонки `urlPrefixes TEXT NOT NULL DEFAULT ''`, `onlyHosts TEXT NOT NULL DEFAULT ''`), `AppDatabase.kt` (version 2, `MIGRATION_1_2`: ALTER TABLE ×2 + INSERT YouTube-категории `enabled = 0`; сид новой БД — обе категории), `CategoryRepository.kt` (сохранение/загрузка новых полей, встроенные слова по `bundledList`), `BlockAction.kt` (PromptKind по `bundledList`)
- Test: `core/src/test/.../match/LocalMatcherTest.kt` (+ кейсы Task 1), `app/src/test/.../data/MigrationTest.kt` (`MigrationTestHelper`, схема 1 экспортирована в `app/schemas`), `CategoryRepositoryTest.kt`

- [ ] **Step 1: Тесты** — Kotlin-копии кейсов Task 1 (`LocalMatcherTest`), плюс `migration keeps categories and adds disabled youtube`, `new database seeds porn and youtube`
- [ ] **Step 2: Run** `./gradlew :core:test` → FAIL (локально; `:app` — CI)
- [ ] **Step 3: Реализовать**
- [ ] **Step 4: Run** `./gradlew :core:test` → PASS; push → CI `build` зелёный
- [ ] **Step 5: Commit** `git commit -m "youtube category on Android: prefix rules, host-scoped words, migration"`

---

### Task 3: Модуль защиты (оба ядра)

**Files:**
- Create: `pc/extension/src/core/lock.js`, `core/src/main/kotlin/app/tabguard/core/lock/Lock.kt`
- Test: `pc/test/lock.test.js`, `core/src/test/.../lock/LockTest.kt`

**Interfaces (одинаковые имена в обоих языках):**
- `LockState = {mode: 'pause'|'partner'|null, pauseMinutes: number, pinHash: string|null, salt: string|null, countdownStartedAt: number|null, unlockedUntil: number, failedAttempts: number, pinLockedUntil: number}`; `initialLockState()` — `mode: null`, `pauseMinutes: 20`.
- `isUnlocked(state, now): boolean` — `mode == null` (ещё не настроено) или `now < unlockedUntil`.
- `settle(state, now): LockState` — если отсчёт истёк: `unlockedUntil = countdownStartedAt + pauseMinutes·60000 + 5·60000`, `countdownStartedAt = null`.
- `startCountdown(state, now)`, `cancelCountdown(state)`; `remainingCountdownMs(state, now): number`.
- `setPauseMode(state, minutes)` (5..60, иначе ошибка), `setPartnerMode(state, pin, salt)` (4–8 цифр, иначе ошибка), `hashPin(salt, pin): string` (sha256 hex; JS — `crypto.subtle`, async → `hashPin` async в JS).
- `tryPin(state, pin, now) → {state, ok}` — при блокировке `ok=false` без счёта; верный → окно 5 мин, сброс счётчика; 5-й неверный → `pinLockedUntil = now + 30 мин`.
- `isWeakening(before: Category|null, after: Category|null): boolean` — удаление; `enabled` true→false; любое поле-список `after ⊉ before`.

- [ ] **Step 1: Тесты** (JS и Kotlin, одинаковые имена)

```
unconfigured lock is open
pause opens a 5 minute window after the countdown
countdown survives restart            // settle с поздним now без промежуточных вызовов
window closes after 5 minutes
cancel countdown keeps it locked
correct pin opens window; wrong does not
pin lockout ends after 30 minutes     // 5 ошибок → заблокирован; +30 мин → верный PIN снова работает
pause minutes outside 5..60 rejected; pin must be 4-8 digits
weakening: disable, delete, removed word/domain/prefix; not weakening: enable, add word
```

- [ ] **Step 2: Run** `cd pc && npm test`; `./gradlew :core:test` → FAIL
- [ ] **Step 3: Реализовать**
- [ ] **Step 4: Run** → PASS
- [ ] **Step 5: Commit** `git commit -m "lock: pause and partner PIN state, weakening rules"`

---

### Task 4: Расширение — защита, мастер, версия

**Files:**
- Create: `pc/extension/options/setup.html`, `setup.js` (мастер), `pc/extension/options/unlock.js` (общие диалоги: отсчёт с кнопкой «Поговорить с Claude», ввод PIN)
- Modify: `pc/extension/src/store.js` (`getLock()`, `saveLock(state)`; `saveCategory`/`setEnabled`/`deleteCategory` принимают `now` и отклоняют ослабление при закрытом окне — `Error('locked')`), `options.html/js/css` (бейдж «Защита: пауза 20 мин / напарник», кнопка «Ослабить защиту», показ окна «Изменения открыты ещё N мин», версия + ссылка «Проверить обновления» на `https://github.com/area-ufa1/AiBlockSite/releases/latest`, поля «Начинается с адреса», «Только на сайтах» в редакторе), `background.js` (`onInstalled` → открыть `setup.html`, если `lock.mode == null`), `pc/e2e/*.smoke.cjs`
- Test: `pc/test/store.test.js` (+), smoke `lock.smoke.cjs`

**Interfaces:** Consumes Task 3 (`lock.js`); хранилище — ключ `lock: LockState`.

- [ ] **Step 1: Тесты**
  - store: `disabling a category without a window is rejected`, `adding a word is allowed while locked`, `deleting is allowed inside the window`
  - smoke: `setup wizard opens on install and saves pause mode` (выбрать «Пауза» 5 мин), `toggle off is refused while locked` (переключатель возвращается, показан диалог), `after the countdown the toggle works` (подменить `countdownStartedAt` в storage на 6 мин назад), `partner pin unlocks` (режим «Напарник» PIN 1234)
- [ ] **Step 2: Run** → FAIL
- [ ] **Step 3: Реализовать**
- [ ] **Step 4: Run** `cd pc && npm test` и smoke ×3 → PASS
- [ ] **Step 5: Commit** `git commit -m "pc: disable protection, setup wizard, version link"`

---

### Task 5: Android — защита, мастер, перехват системных экранов

**Files:**
- Create: `core/src/main/kotlin/app/tabguard/core/lock/ProtectedScreens.kt`, `app/.../ui/SetupScreen.kt`, `app/.../ui/UnlockDialog.kt`
- Modify: `SettingsStore.kt` (`lockState: Flow<LockState>`, `saveLock`), `MainViewModel.kt` (ослабление через `isWeakening`/`isUnlocked`, иначе `state.lockMessage`), `CategoriesScreen.kt`/`CategoryEditScreen.kt` (поля префиксов и сайтов; переключатель при закрытом окне открывает диалог), `MainActivity.kt` (мастер, пока `mode == null`; версия + «Проверить обновления»), `TabGuardAccessibilityService.kt` (события от пакетов настроек/установщика → `ProtectedScreens.isProtected(pkg, root)` → если закрыто: `GLOBAL_ACTION_BACK` + Toast «Отключение защищено — откройте Страж вкладок»), `accessibility_service.xml` (без изменений типов событий), `strings.xml`
- Test: `core/src/test/.../lock/ProtectedScreensTest.kt`, `app/src/test/.../ui/MainViewModelTest.kt` (+)

**Interfaces:**
- `object ProtectedScreens { const val APP_NAME = "Страж вкладок"; fun isProtected(packageName: String, root: UiNode): Boolean }` — пакеты: `com.android.settings`, `com.google.android.packageinstaller`, `com.android.packageinstaller` и любой пакет, оканчивающийся на `.settings`; защищён, если на экране есть узел с текстом ровно `APP_NAME` **и** (кнопка/текст из набора `["Остановить", "Принудительно остановить", "Force stop", "Удалить", "Uninstall"]` **или** переключатель (`className` содержит `Switch`) рядом с подписью «Использовать Страж вкладок»/`Use Страж вкладок`/«Включить»), **или** диалог установщика с текстом, содержащим `APP_NAME` и «удалить»/`uninstall`.

- [ ] **Step 1: Тесты** (`FakeNode` деревья)

```kotlin
@Test fun `app info page with force stop is protected`()
@Test fun `accessibility page of the service is protected`()
@Test fun `uninstall dialog is protected`()
@Test fun `app list mentioning the name is not protected`()     // список приложений: «Страж вкладок» + другие имена, без кнопок
@Test fun `settings search result is not protected`()          // строка поиска с «Страж» и результатами
@Test fun `other app info page is not protected`()
// MainViewModelTest
@Test fun `toggle off while locked is refused with message`()
@Test fun `setup is required until a mode is chosen`()
```

- [ ] **Step 2: Run** `./gradlew :core:test` → FAIL
- [ ] **Step 3: Реализовать**
- [ ] **Step 4: Run** `./gradlew :core:test` → PASS; push → CI зелёный
- [ ] **Step 5: Commit** `git commit -m "app: disable protection, setup wizard, protected settings screens"`

---

### Task 6: Репозиторий к открытию и релизы

**Files:**
- Modify: `app/build.gradle.kts` (подпись из `TABGUARD_KEYSTORE`/`TABGUARD_KEYSTORE_PASSWORD`/`TABGUARD_KEY_ALIAS`/`TABGUARD_KEY_PASSWORD`, иначе debug по умолчанию; `versionName` из env `TABGUARD_VERSION`, иначе `0.0.0-dev`), `pc/scripts/package.sh` (версия в `manifest.json` из `TABGUARD_VERSION`; zip `pc/dist/TabGuard-PC.zip`), `.github/workflows/android.yml` (декодирование `ANDROID_KEYSTORE_BASE64` в файл, если секрет есть)
- Create: `.github/workflows/release.yml` (on push tags `v*`; `permissions: contents: write`; тесты → сборка APK (release-сигнатура) → `TabGuard-Android.apk` + `TabGuard-PC.zip` → `softprops/action-gh-release@v2`)
- Delete from history: `app/debug.keystore`

- [ ] **Step 1:** `base64 -w0 app/debug.keystore` → человек добавляет секреты `ANDROID_KEYSTORE_BASE64` и `ANDROID_KEYSTORE_PASSWORD=android` (alias/key password — `androiddebugkey`/`android`) в Settings → Secrets → Actions. (Тот же ключ — обновления встают поверх.)
- [ ] **Step 2:** Изменения сборки; push → CI зелёный и APK подписан тем же ключом (`apksigner verify --print-certs` — SHA-256 совпадает с текущим).
- [ ] **Step 3: СТОП — подтверждение человека** на перезапись истории. Затем: `git filter-repo --path app/debug.keystore --invert-paths`, `git push --force` в `ccr-c2a2d1c3-8f2cum` и `main`; `git log --all -- app/debug.keystore` → пусто.
- [ ] **Step 4:** Метка `v1.0.0` → workflow `release` зелёный, у релиза два файла.
- [ ] **Step 5:** Сказать человеку включить «Public» (Settings → General → Danger Zone).

---

### Task 7: Страница-инструкция

**Files:**
- Create: `site/index.html`, `site/style.css`, `site/img/*.png` (скриншоты мастера, настроек, страницы расширений — снять Playwright-скриптом `site/screenshots.cjs` из `pc/dist`), `.github/workflows/pages.yml` (on push to `main` с изменениями в `site/**`; `actions/upload-pages-artifact@v3` + `actions/deploy-pages@v4`)

Разделы — из спеки (раздел 5); ссылки на `https://github.com/area-ufa1/AiBlockSite/releases/latest`. iPhone: «Настройки → Экранное время → Контент и конфиденциальность → Магазины… → Приложения: запретить YouTube» (или «Лимиты приложений → YouTube 1 мин + блокировка по окончании»), «Ограничения контента → Веб-контент → Ограничить сайты для взрослых», «Использовать код-пароль Экранного времени» — вводит напарник или придумать и записать далеко.

- [ ] **Step 1: Run** `node site/screenshots.cjs` → PNG в `site/img/`
- [ ] **Step 2:** Снимок страницы в Chromium на ширине 390 и 1280 — без горизонтальной прокрутки, все картинки грузятся
- [ ] **Step 3: Commit, push**; после открытия репозитория человек включает Pages: Settings → Pages → Source «GitHub Actions»; workflow `pages` зелёный, адрес открывается
