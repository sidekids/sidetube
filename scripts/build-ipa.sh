#!/usr/bin/env bash
# Baut eine signierte .ipa von SideTube.
#
#   scripts/build-ipa.sh <TEAM_ID> [--install <Geräte-ID>]   # ad-hoc, für registrierte Geräte
#   scripts/build-ipa.sh <TEAM_ID> --testflight              # für App Store Connect / TestFlight
#
# Ad-hoc läuft zwölf Monate, verlangt aber auf dem Gerät den Entwicklermodus. TestFlight-Builds
# brauchen ihn nicht, laufen dafür 90 Tage je Build.
#
# Voraussetzung: Mitgliedschaft im Apple Developer Program, in Xcode ist ein Konto angemeldet
# (Einstellungen -> Accounts; ohne das bricht der Export mit "No Accounts" ab), das Zielgerät ist
# im Portal registriert und die App-ID xyz.steier.sidetube existiert. Die Geräte-ID für --install
# liefert `xcrun devicectl list devices`.
set -euo pipefail

cd "$(dirname "$0")/../ios"
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"

TEAM_ID="${1:-${SIDETUBE_TEAM_ID:-}}"
if [ -z "$TEAM_ID" ]; then
    echo "Team-ID fehlt. Aufruf: scripts/build-ipa.sh <TEAM_ID> [--install <Geräte-ID>|--testflight]" >&2
    exit 1
fi
shift || true

DEVICE=""
METHOD="release-testing"
case "${1:-}" in
    --install)
        DEVICE="${2:-}"
        [ -n "$DEVICE" ] || { echo "--install braucht eine Geräte-ID." >&2; exit 1; }
        ;;
    --testflight)
        METHOD="app-store-connect"
        ;;
esac

ARCHIVE="build/sidetube.xcarchive"
EXPORT_DIR="build/ipa"
OPTIONS="build/ExportOptions.plist"

xcodegen generate

# Die Team-ID wird nur für diesen Lauf gesetzt; project.yml bleibt unangetastet.
rm -rf "$ARCHIVE" "$EXPORT_DIR"
xcodebuild archive \
    -project sidetube.xcodeproj -scheme sidetube \
    -destination 'generic/platform=iOS' \
    -archivePath "$ARCHIVE" \
    -allowProvisioningUpdates \
    DEVELOPMENT_TEAM="$TEAM_ID"

mkdir -p "$(dirname "$OPTIONS")"
cat > "$OPTIONS" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>            <string>$METHOD</string>
    <key>teamID</key>            <string>$TEAM_ID</string>
    <key>signingStyle</key>      <string>automatic</string>
    <key>destination</key>       <string>export</string>
    <key>stripSwiftSymbols</key> <true/>
    <key>uploadSymbols</key>     <false/>
</dict>
</plist>
PLIST

xcodebuild -exportArchive \
    -archivePath "$ARCHIVE" \
    -exportOptionsPlist "$OPTIONS" \
    -exportPath "$EXPORT_DIR" \
    -allowProvisioningUpdates

IPA="$(find "$EXPORT_DIR" -maxdepth 1 -name '*.ipa' | head -1)"
[ -n "$IPA" ] || { echo "Kein .ipa im Export gefunden." >&2; exit 1; }
echo "Fertig: $IPA"

if [ -n "$DEVICE" ]; then
    echo "Installiere auf $DEVICE …"
    xcrun devicectl device install app --device "$DEVICE" "$IPA"
fi

echo
if [ "$METHOD" = "app-store-connect" ]; then
    echo "Für TestFlight: Datei in App Store Connect hochladen (Transporter oder Xcode Organizer)."
    echo "Der App-Eintrag mit der Bundle-ID muss dort vorher existieren."
else
    echo "Die Signatur gilt zwölf Monate. Neue Geräte müssen vor dem Export im Portal"
    echo "registriert werden – ihre UDID steckt im Profil und damit in dieser Datei."
    echo "Auf dem Gerät verlangt eine ad-hoc-App den Entwicklermodus."
fi
