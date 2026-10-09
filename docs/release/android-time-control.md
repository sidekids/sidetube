# Android time-control verification — partial implementation

Daily limits and quiet hours now enforce playback restrictions in the developer-based Android client. Core tests are not proof of device-level parental-control reliability. No release gate is closed by this document alone.

## Implemented semantics

- Daily limits: query persisted remaining time before starting; await outstanding history writes; retain a monotonic session budget across queue/autoplay changes; stop and close when exhausted. Pause, loading and buffering do not consume viewing time. Persist fractional seconds rounded up. Storage errors stop further playback within the ViewModel lifetime.
- Quiet hours: evaluate local wall-clock minutes, with later starts Friday/Saturday, an exclusive end and a time-limited stored parent exception. Equal start/end follows the iOS full-window semantics. Invalid enabled settings block playback. Parent settings updates invalidate the current player through profile observation.
- Scheduling: one daily-budget deadline while playing plus one quiet-hours boundary deadline while a player exists. No repeating home-screen timer. Pause cancels the daily-budget deadline but not the quiet-hours boundary. Close/background cancels both. System clock/time-zone changes reevaluate the quiet-hours rule while the receiver is registered for the player session.
- Since 2026-10-08 Android presents the PIN-locked full-screen lock (`KidSperreView`, [ADR 0007](../adr/0007-android-vollbild-sperre.md)) for quiet hours, sleep-timer expiry, exhausted daily limit, storage failure and interrupted sessions – also while browsing, with one non-repeating deadline for the next boundary. Earlier text in this file describing a dismissible message is historical.

## Interrupted-session recovery (2026-09-10)

The agreed recovery policy is parent-PIN authorization rather than silently consuming the entire remaining daily allowance. Android database schema 2 adds a small `playback_sessions` table: profile ID, random session token and start timestamp, with no extra video/title log. A marker is committed before a player can be created or loaded. Normal close removes it only after queued watch-history writes finish successfully. A write error, interrupted close or process termination leaves the marker present. A subsequent start for that profile refuses playback until parents explicitly acknowledge it in the PIN-gated profile list.

The confirmation explains that unrecorded time may be unknown. Acknowledgement removes only the marker: it neither deletes already saved history nor fabricates or refunds viewing time. It is never automatic at startup or midnight. Tokens prevent a late completion from deleting a newer session's marker. Profile deletion cascades its own marker; other profiles remain independent. Entering the parent PIN screen cancels an outstanding player-start request.

Migration 1→2 creates the new table without rewriting existing records. A file-backed Robolectric migration test reconstructs the checked-in version-1 schema, verifies retained profile/time data and validates Room's version-2 schema. Further file-backed tests cover reopen, duplicate admission, stale completion tokens, parent acknowledgement and profile isolation. These do not substitute for force-stop/device/UI integration tests.

## Automated evidence

`PlaybackModelTest` covers paused/buffering time, error attribution, queue/autoplay boundaries, exhausted/invalid/unlimited budgets, duplicate state events and short-session rounding. `BedtimePolicyTest` covers weekday/weekend rules, midnight, inclusive start/exclusive end, exception expiry, invalid settings, exact boundaries, DST gaps/repeated hours and supplied zones. Repository tests reject invalid budgets/negative history and prevent 64-bit SQLite sums being truncated into extra time. The full Android suite has 115 passing core tests after this change; debug/release builds, AAB and lint pass.

## Required integration/device matrix — not executed

Use an isolated test device and synthetic profile/content. Change clock/zone only on that test device, restoring its settings afterward. Record OS, WebView version, build commit and playback/network evidence.

| Scenario | Required result |
|---|---|
| Start with zero remaining budget | No WebView player/media request; explanatory message |
| Reach limit while playing | Audio and video stop, bridge released, correct history saved once |
| Pause/buffer/resume | Budget frozen during pause/buffering; resume uses actual remaining time |
| Next/previous/autoplay near limit | No reset of session budget; no extra load after exhaustion |
| Close and immediately reopen | Start waits for previous history writes; no stale budget |
| Slow/failed history storage | No playback admitted on a failed read; write failure closes playback |
| Quiet hours start during playing/paused/loading states | Player closes at boundary; no resumed/background audio |
| Parent exception expires | Block at the stored timestamp if the quiet-hours window is still active |
| Parent changes profile/settings | Old player/deadlines invalidated; updated rules apply |
| Clock/zone change and DST transition | Reevaluate local quiet hours without waiting for an old deadline |
| Background/foreground and 50 player round trips | No retained receivers/jobs/players; reentry checks current rules |
| Home idle 10 minutes | No time-control polling, player or scheduled deadline |
| Force-stop during begin/play/record/finish | Marker survives uncertain completion; no playback after restart without parent acknowledgement. **PASS for the plain force-stop-while-playing case (2026-09-13, see [device verification](android-device-verification.md)); adversarial timing exactly during `begin`/`record`/`finish` remains untested.** |
| Cancel parent recovery dialog / wrong PIN | Marker and saved history remain unchanged |
| Confirm recovery after correct PIN | Only selected marker removed; saved time preserved; no implicit new playback. **PASS, 2026-09-13 (see [device verification](android-device-verification.md)).** |

## Known blockers

The durable marker blocks automatic restart after uncertain termination, but cannot reconstruct exact uncommitted viewing time. Force-stop timing, cancellation windows, storage faults and the complete PIN/recovery UI require device/integration testing before this release gate can pass. Midnight allocation (2026-09-13): watch-history attribution is now split per calendar day a session's playing stretch crosses, instead of attributing the whole segment to whichever day it was recorded on - see [pre-release-audit.md](pre-release-audit.md#android). The session's *budget* still deliberately retains its initial pre-midnight value for the whole session rather than switching to a fresh daily allowance at midnight; that is unchanged. Device clock manipulation remains a local-device threat-model issue; monotonic playback accounting does not make calendar-day rules tamper-proof. Parent bedtime-exception UI, the full-screen lock and sleep mode have landed since (2026-09-12 to 2026-10-08); device validation of all three is still open. iOS now has an equivalent durable marker (see [ios-watch-time-failures.md](ios-watch-time-failures.md)), but real force-stop/PIN-UI device testing is still open on both platforms. The application is not release-ready.
