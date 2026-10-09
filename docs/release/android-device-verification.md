# Android physical-device verification — partial

2026-09-10: a user-authorized in-place Debug update was performed on a SidePhone SP-01, Android 12 / API 31. The reported system WebView is `com.android.webview` 91.0.4472.114. This is the observed test environment, not a security approval of that WebView version.

## In-place update and migration: PASS for this installation

- The old and new APK signing-certificate SHA-256 digests matched. The installed application ID remained `xyz.steier.sidetube.debug`.
- Before updating, the app was stopped and its existing APK and accessible private app files were backed up outside the repository. Android Keystore keys are not included; this is not a portable full-device recovery backup.
- `adb install -r` succeeded without uninstalling, clearing data, resetting the parent PIN or requesting a downgrade. Product version remains 0.1.0, build 1; target SDK changed from 35 to 36. The original installation timestamp was retained.
- Launch returned successfully. A stopped-app database snapshot afterward reports schema version 2 rather than 1 and an empty `playback_sessions` table.
- Every original row in profiles, whitelist, watch history, curated sources, review events and channel cache was compared with the pre-update snapshot and remained unchanged. Startup added two missing bundled source-register entries; it did not overwrite existing ones.

The tested APK SHA-256 is `6a7727416f29210c84ad6d255746bf45066f8956813da3dc74b98d6c1352330d`. The working tree was clean at `963e48a` before this device documentation. Backups, screenshots and database contents remain private; no device serial, profile names, content identifiers or PIN material is included here.

## Not yet verified

The device remained on the Android keyguard. Launch/migration success is not proof of a usable foreground UI, correct parental PIN entry, video playback, accessibility, or WebView compatibility. The app was stopped for a consistent post-update snapshot. No user content was played, no synthetic profile was inserted, and no app data was cleared.

After the owner unlocks the device, test foreground navigation, parent gates and playback/exit behavior using owner-approved or synthetic test content. Obtain a dedicated test profile or explicit permission before altering approvals, time limits or other existing child data. Force-stop/admission races, real Room-to-WebView revocation, offline/provider errors, network capture and the energy gates remain open. Do not infer their results from this successful database upgrade.

## 2026-09-13: first foreground interruption-marker verification — PASS

The device was unlocked and in normal use (another owner app in the foreground); the SideTube foreground UI was reached without altering that app's state. Existing owner content and an existing profile were used read-only; no data was cleared, no PIN was entered or guessed, and no profile/content name is recorded here.

- Started existing approved video playback normally; confirmed real playback progress (not merely "loading").
- `am force-stop` on the app package while playing, simulating an uncontrolled process kill (not the app's own close path).
- Relaunched: attempting to play *any* video for that profile (two different videos tried) is refused with the expected message ("Die letzte Wiedergabe wurde unterbrochen. Bitte die Eltern um Freigabe bitten.") instead of starting playback. This is the first real-device confirmation of the durable admission-marker mechanism ([interrupted-session recovery](android-time-control.md)) since it was implemented — previously verified only by file-backed Robolectric tests.
- The parent-PIN screen was reached and renders correctly. Completing the recovery step itself (entering the PIN, confirming the "Unterbrochene Wiedergabe prüfen" dialog, verifying resumed playback) was **not** performed — this instance's parent PIN was not available, and it was not guessed or brute-forced. Left the app cleanly via system back from the PIN screen; the device returned to its original foreground app unaffected.

### Still not verified

The subframe-navigation and foreign-video guards' actual click-through behavior and calendar-day-splitting remain open on physical hardware.

## 2026-09-13, later same day: PIN reset, full recovery-flow completion, and an unresolved playback stall

The owner did not know the existing parent PIN. With explicit authorization, it was reset by deleting only `PinStore`'s own `EncryptedSharedPreferences` file (`shared_prefs/sidetube-parent.xml` under the app's private storage via `run-as`) - a separate store from the Room database and from the new `ProfilePreferences` plain-prefs file, so no profile/whitelist/watch-history data was touched. The file was backed up before deletion. The app's own "PIN festlegen" setup flow was then used to set a new one; the owner was told the new PIN directly, out of band from this document (never written here, matching the existing no-PIN-material rule).

With the new PIN:
- **Interrupted-session recovery, completed end to end for the first time**: entered the PIN, opened "Unterbrochene Wiedergabe prüfen", confirmed "Als Eltern freigeben", got "Unterbrochene Wiedergabe freigegeben. Gespeicherte Sehzeit bleibt erhalten." This closes the gap the previous entry left open.
- **A device-level in-place update was also confirmed to preserve an unresolved marker**: the marker being recovered above had survived not just an app relaunch (previous entry) but a full `adb install -r` update in between - a stronger durability result than previously recorded.
- **Playback itself could not be confirmed working on this pass.** Repeated attempts to start a video got stuck at "Lädt… · 0:00" and never reached a playing state, including after single, unhurried, uninterrupted attempts of 60+ seconds. To isolate whether this was caused by the 2026-09-12 cookie/nocookie-host change, the host was reverted to `www.youtube.com` in a local build and reinstalled: the same stall reproduced. A separate build from the pre-cookie-change commit (`56f6954`, the same commit whose code was confirmed playing correctly earlier the same morning) was then installed in its place: **the same stall reproduced there too.** This rules out the recent cookie/sleep-timer/nocookie changes as the cause of this specific session's stall - something about the device or WebView's state after many repeated installs/force-stops within one session is the more likely explanation, not a code regression. The current, correct (post-merge, nocookie-host) build was reinstalled afterward and any marker it left behind was cleared the same way, so the device was not left blocked.
- This means the nocookie-host/session-cookie change from 2026-09-12 is **still functionally unverified** - neither confirmed working nor confirmed broken by this session. It should be retested cleanly (ideally after a device reboot, and by the device's actual users during normal use rather than repeated automated install cycles) before relying on it.

Evidence (screenshots, pulled `sidetube.db`/`-wal`/`-shm` snapshots pre- and post-reset, the deleted `sidetube-parent.xml`, and the pre-reset APK) is stored outside the repository per the existing rule; no device serial, profile name, content identifier or PIN is included here.

## 2026-10-08, emulator SP-01 (not a physical device): player hardening and full-screen lock

Environment: the `SidePhone_SP01` emulator (API 31, 480×640, Google system image with the current Chromium WebView), Debug build, parent PIN set for the emulator only. This is emulator evidence; it does not replace the physical-device checks above, in particular not the old-WebView (91) case.

- **Full-screen lock ([ADR 0007](../adr/0007-android-vollbild-sperre.md))**: with quiet hours enabled and the clock inside the window, returning to kid mode showed the PIN-locked „Schlafenszeit" lock with the resume time; taps on the search icon and the list underneath had no effect; „Für Eltern" → PIN set the exception until the window end (visible in the parent list as „Ruhezeit ausgesetzt bis 06:30") and returned to an unlocked kid mode; „Aufheben" in the parent area re-locked kid mode on return. A 5-minute sleep timer started from the parent area ended, after real elapsed time, in the „Gute Nacht!" lock without a player. The centre key on the lock opened the PIN pad (first attempt did not react, second did; not reproduced). Note for testers: setting the emulator clock with `date` as root does **not** broadcast `ACTION_TIME_CHANGED`, so deadlines run in real time.
- **`controls: 0` and no third-party cookies**: an approved video (added by link, approved in the review list) started within about 10 s and played with sound and captions; the app's own state line went „Lädt …" → „Spielt". YouTube's progress bar, settings and captions buttons disappeared; the embed's info bar (title, channel avatar, share/copy-link, logo) still appears on load and pause, as on iOS. Taps on the avatar and the logo did not leave the app (the logo tap paused the video, which the app mirrored as „Pause"); the share button showed YouTube's own „Unable to copy link to clipboard" and nothing else. The baseline build (`controls: 1`, third-party cookies accepted) played equally on the same emulator, so the change did not break playback here.
- The position label stayed at 0:00 while playing. Traced on 2026-10-09 through the WebView's DevTools (hooked `post`): the page's 5-second ticker restarted on every buffering/playing toggle (3 → 1), which the emulator produces often, so the first position arrived only after 11 s of playback. Fixed in `player.html` (ticker survives buffering, posts once immediately); re-verified on the emulator the same day („Spielt · 0:15" while the video was at about 17 s).
- Found alongside: with `controls: 0` YouTube reports ended (0) and then unstarted/cued (-1, 5) on its own, and `stopVideo()` reports the same pair; `PlaybackModel.onState` let those override the „Fertig" end card with „Lädt …". The model now ignores player states and positions after the end, as iOS's `PlayerModel.handle` does, and answers a renewed playing state with a stop (`PlaybackModelTest`, one new case). Emulator: the end card appears after the video.
