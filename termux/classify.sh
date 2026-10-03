#!/data/data/com.termux/files/usr/bin/bash
# $1 — полный промпт. Печатает ответ модели в stdout.
exec claude -p --model haiku "$1"
