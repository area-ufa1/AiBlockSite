#!/data/data/com.termux/files/usr/bin/bash
# $1 — полный промпт. Печатает ответ модели в stdout.
# Claude Code has no Android build, so it runs inside Ubuntu (proot-distro).
# No tools, own system prompt, no background traffic: faster start, answers only with a number.
# timeout: never leave a hung claude running after the app gave up (90 s).
exec timeout 85 proot-distro login ubuntu -- env DISABLE_AUTOUPDATER=1 CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1 \
  /root/.local/bin/claude -p --model haiku --tools "" \
  --system-prompt "Ты классификатор. Отвечай только числом." "$1"
