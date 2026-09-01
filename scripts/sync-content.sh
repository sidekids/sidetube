#!/usr/bin/env bash
# Kopiert die gemeinsamen Kuratierungsdaten aus content/ in ein Zielprojekt.
# Verbindlich ist immer content/ – die Kopien sind Build-Artefakte und gehören in die .gitignore des Ziels.
#
#   scripts/sync-content.sh ios                       # in dieses Projekt (nur nötig ohne Ordnerreferenz)
#   scripts/sync-content.sh android [Pfad zum Repo]   # nach app/src/main/assets/content
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
src="$root/content"
[ -d "$src" ] || { echo "content/ nicht gefunden"; exit 1; }

case "${1:-}" in
  ios)
    dest="$root/ios/Sources/Resources/content"
    ;;
  android)
    repo="${2:-$root/android}"
    dest="$repo/app/src/main/assets/content"
    [ -d "$repo" ] || { echo "Android-Repo nicht gefunden: $repo"; exit 1; }
    ;;
  *)
    echo "Aufruf: $0 {ios|android} [Zielrepo]"; exit 2
    ;;
esac

mkdir -p "$dest"
rsync -a --delete "$src/" "$dest/"
echo "$(find "$dest" -name '*.json' | wc -l | tr -d ' ') Dateien nach $dest kopiert"
