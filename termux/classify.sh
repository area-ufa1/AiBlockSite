#!/data/data/com.termux/files/usr/bin/bash
# $1 — полный промпт. Печатает ответ модели в stdout.
# timeout: never leave a hung claude running after the app gave up (30 s).
exec timeout 25 claude -p --model haiku "$1"
