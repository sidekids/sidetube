# Device and owner checklist

Everything that still blocks a release and cannot be closed from the desk: it needs the physical SidePhone SP-01 (Android 12, WebView 91), a real iPhone, or a decision by the owner. Tick an item only with evidence (screenshot, log line, or a sentence in [android-device-verification.md](android-device-verification.md) / [ios-compatibility.md](ios-compatibility.md)); the emulator and the simulator do not count here.

## Android – physical SP-01

| # | What to verify | How | Evidence |
|---|---|---|---|
| A1 | Playback starts and runs with `controls: 0` and **no third-party cookies** on WebView 91 | Install the current debug build, approve one short video, start it, wait 30 s; repeat after a reboot | Status line „Spielt“ with advancing position; note WebView version from `adb shell dumpsys package com.google.android.webview` or the device's WebView |
| A2 | The 2026-09-13 playback stall | Same as A1 on a freshly rebooted device, no reinstalls in between; if it stalls, capture `adb logcat` and the page state through the WebView DevTools hook described in `android-device-verification.md` (2026-10-09 entry) | Either „does not reproduce after reboot“ or a logcat excerpt |
| A3 | Click-through on YouTube's info bar | While playing, tap channel avatar, title, share, logo | App stays in the player; nothing opens youtube.com |
| A4 | Full-screen lock: quiet hours | Enable quiet hours in the profile, set the device clock into the window, return to kid mode | Lock „Schlafenszeit“ with resume time; ring and taps do nothing; „Für Eltern“ → PIN sets the exception; lock lifts at the window end |
| A5 | Full-screen lock: sleep timer | Start a 5-minute timer, leave the app in the foreground and once in the background | „Gute Nacht!“ after 5 minutes in both cases; note whether Doze delayed it |
| A6 | Full-screen lock: daily limit | Set a 5-minute limit, play past it | „Die Zeit ist um“ during playback and again when starting the next video; lifts after midnight or a raised limit |
| A7 | Interrupted session | `adb shell am force-stop` during playback, reopen | Lock „Wiedergabe unterbrochen“; parent recovery in Settings clears it |
| A8 | Wheel-only operation of „Eltern benachrichtigen“ (ADR 0005) | Reach the page and every button with the ring only | Each control reachable and triggerable |
| A9 | Energy baseline | 30 minutes playback, 30 minutes idle on the home screen | Battery delta from the device's own battery stats |
| A10 | Signed update keeps data | Install the signed test APK over the previous signed build | Profiles, approvals, PIN and watch history survive |

## iOS – physical iPhone

| # | What to verify | How | Evidence |
|---|---|---|---|
| I1 | QR scan of the parent-channel setup code | Settings → Eltern benachrichtigen → scan the code from a screen | Setup stored, „Test senden“ arrives in the Nextcloud app |
| I2 | Signed-app upgrade with existing data | TestFlight or ad-hoc build over the previous one | Profiles, approvals, history survive; interrupted-session marker survives |
| I3 | Force-stop during playback and PIN recovery | Swipe the app away while playing, reopen | Overlay „Wiedergabe unterbrochen“, parent PIN recovery works |
| I4 | VoiceOver and large text | Walk kid mode and the review queue | Every control announced; nothing clipped at the largest text size |
| I5 | English | Device language English, open kid mode and the review queue | No German leftovers in the UI (data such as wish titles may stay German) |

## Owner decisions

| # | Decision | Where it is recorded once taken |
|---|---|---|
| O1 | ~~Target audience~~ **Decided 2026-10-09 ([ADR 0009](../adr/0009-oeffentliche-fassung-kanal.md)): public, families in general.** Still open: the child-directed provider model (YouTube terms, COPPA-style designation) before the store phase | `pre-release-audit.md`, store checklists |
| O2 | Provenance of the Android replacement and rights to every bundled asset and seed library | `repository-audit.md`, `THIRD_PARTY_NOTICES.md` |
| O3 | Which remaining parity gaps block the first version (starter-pack welcome flow, deep links, PeerTube; watch statistics landed on 2026-10-09) | `feature-parity.md` |
| O4 | Store disclosures: camera permission, outgoing Nextcloud request, data safety / app privacy | `store-android.md`, `store-ios.md` |
| O5 | ~~Channel~~ **Decided 2026-10-09 (ADR 0009): GitHub release first, stores later.** Each push, release or store submission still needs the owner's explicit go | `adr/0006`, `adr/0009`, release notes |

Last updated 2026-10-09.
