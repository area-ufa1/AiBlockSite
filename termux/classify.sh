#!/data/data/com.termux/files/usr/bin/bash
# $1 — полный промпт. Печатает ответ модели в stdout.
# Claude Code has no Android build, so it runs inside Ubuntu (proot-distro).
# timeout: never leave a hung claude running after the app gave up (30 s).
exec timeout 25 proot-distro login ubuntu -- /root/.local/bin/claude -p --model haiku "$1"
