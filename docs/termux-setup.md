# Настройка Termux + Claude Code для «Стража вкладок»

Слой проверки через Claude вызывает `claude -p` в Termux на этом же телефоне.
Без этой настройки приложение работает только по словам и доменам.

## 1. Termux

Установите Termux **с F-Droid или со страницы релизов на GitHub** (termux/termux-app).
Версия из Play Store устарела и не поддерживает нужный интент.

## 2. Claude Code

```bash
pkg update && pkg install nodejs
npm i -g @anthropic-ai/claude-code
claude          # войти под аккаунтом с подпиской Pro, затем выйти (/exit)
```

## 3. Скрипт-обёртка

Скопируйте `termux/classify.sh` из репозитория в Termux:

```bash
mkdir -p ~/.tabguard
# вставьте содержимое classify.sh, например: nano ~/.tabguard/classify.sh
chmod +x ~/.tabguard/classify.sh
```

## 4. Разрешить внешним приложениям запускать команды

```bash
mkdir -p ~/.termux
echo "allow-external-apps=true" >> ~/.termux/termux.properties
termux-reload-settings
```

Затем: Настройки Android → Приложения → «Страж вкладок» → Разрешения →
«Run commands in Termux environment» → Разрешить.

## 5. Проверка

```bash
time ~/.tabguard/classify.sh "Ответь только цифрой 0"
```

Ожидается вывод `0`. Запишите сюда время ответа:

| Дата | Телефон | Результат | Время |
|------|---------|-----------|-------|
|      |         |           |       |

Если `claude` в Termux не запускается — запишите ошибку сюда; слой Claude
в приложении останется выключенным, остальное работает.
