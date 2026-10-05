#!/usr/bin/env bash
# Kopiert nur explizit zur Veröffentlichung vorgesehene Kuratierungsdaten.
# Verbindlich ist immer content/ – die Kopien sind Build-Artefakte und gehören in die .gitignore des Ziels.
#
#   scripts/sync-content.sh ios       # nach ios/build/generated/content
#   scripts/sync-content.sh android   # nach android/app/build/generated/sidetubeAssets/content
#
# Android verwendet beim Build einen eigenen Gradle-Sync-Task mit demselben Manifest.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
src="$root/content"
[ -d "$src" ] || { echo "content/ nicht gefunden"; exit 1; }

case "${1:-}" in
  ios)
    dest="$root/ios/build/generated/content"
    ;;
  android)
    dest="$root/android/app/build/generated/sidetubeAssets/content"
    ;;
  *)
    echo "Aufruf: $0 {ios|android}"; exit 2
    ;;
esac

manifest="$src/public-files.txt"
while IFS= read -r path; do
  case "$path" in
    ''|/*|*..*|private-*|*/private-*) echo 'Invalid public content path' >&2; exit 1 ;;
  esac
  test -f "$src/$path" || { echo "Missing public content: $path" >&2; exit 1; }
done < "$manifest"
mkdir -p "$dest"
rsync -a --delete-excluded --files-from="$manifest" "$src/" "$dest/"
echo "$(find "$dest" -name '*.json' | wc -l | tr -d ' ') Dateien nach $dest kopiert"
