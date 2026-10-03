# Настройка Termux + Claude Code для «Стража вкладок»

Слой проверки через Claude вызывает `claude -p` в Termux на этом же телефоне.
Без этой настройки приложение работает только по словам и доменам.

## 1. Termux

Установите Termux **с F-Droid или со страницы релизов на GitHub** (termux/termux-app).
Версия из Play Store устарела и не поддерживает нужный интент.

## 2. Claude Code (внутри Ubuntu)

Готовой сборки Claude Code под Android нет, поэтому он запускается в Ubuntu внутри Termux.

```bash
pkg upgrade -y                      # на вопросы о файлах конфигурации отвечать y
pkg install -y proot-distro
proot-distro install ubuntu
proot-distro login ubuntu
# теперь внутри Ubuntu (root@localhost):
apt update && apt install -y curl
curl -fsSL https://claude.ai/install.sh | bash   # при обрывах: уведомление Termux → Acquire wakelock
~/.local/bin/claude --version
~/.local/bin/claude                 # войти под аккаунтом с подпиской Pro, затем /exit
exit                                # обратно в Termux
```

## 3. Скрипт-обёртка

```bash
mkdir -p ~/.tabguard && cat > ~/.tabguard/classify.sh <<'SH'
#!/data/data/com.termux/files/usr/bin/bash
exec timeout 85 proot-distro login ubuntu -- /root/.local/bin/claude -p --model haiku "$1"
SH
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
