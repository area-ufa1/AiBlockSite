#!/usr/bin/env bash
# Builds pc/dist/: TabGuard/ (the extension + bundled blocklist), install.bat, УСТАНОВКА.txt.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIST="$ROOT/pc/dist"

rm -rf "$DIST"
mkdir -p "$DIST/TabGuard/blocklists"
cp -r "$ROOT/pc/extension/." "$DIST/TabGuard/"
cp "$ROOT/app/src/main/assets/blocklists/adult.txt" "$DIST/TabGuard/blocklists/adult.txt"
cp "$ROOT"/app/src/main/assets/blocklists/*-keywords.txt "$DIST/TabGuard/blocklists/"
for f in install.bat УСТАНОВКА.txt; do
  if [ -f "$ROOT/pc/$f" ]; then cp "$ROOT/pc/$f" "$DIST/$f"; fi
done
VERSION="${TABGUARD_VERSION:-}"
VERSION="${VERSION#v}"
if [ -n "$VERSION" ]; then
  node -e 'const f=process.argv[1],m=JSON.parse(require("fs").readFileSync(f));m.version=process.argv[2];require("fs").writeFileSync(f,JSON.stringify(m,null,2)+"\n")' \
    "$DIST/TabGuard/manifest.json" "$VERSION"
fi
(cd "$DIST" && rm -f TabGuard-PC.zip && zip -qr TabGuard-PC.zip TabGuard install.bat УСТАНОВКА.txt)
echo "packaged into $DIST"
