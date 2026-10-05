# Android authorization changes during playback

Follow-up: 2026-09-10. The visible-whitelist collector previously closed playback only while the current screen was a channel. A Home/Search player could retain an already-created queue after a queued item's approval changed. A start suspended on storage could also retain a stale queue when another queued item, rather than the selected item, was revoked.

Every authorization-flow emission now closes active playback and cancels a pending start before publishing updated rows, independently of the screen. Existing channel-list invalidation remains. This is deliberately conservative: even a source-policy emission leaving the approved list unchanged invalidates the session. No equality filter is used because that could hide changes to channel-candidate authorization. The child receives an explanation and can select content again under the current rules.

Normal close uses the existing watch-time/marker completion path. Cancellation after an admission may have committed leaves its marker for parent recovery; it never admits a player from that cancelled request. No periodic policy polling or automatic playback restart was introduced.

## Tests and limits

Three app-module ViewModel tests use the real repositories, content policy and playback model with controlled DAO flows and coroutine suspension points. They exercise:

- Home and Search with loading, playing and paused models: revoking another queued item closes playback, clears a clean session marker and ignores late player callbacks.
- A source emission while the budget read is suspended: the pending start is cancelled even when visible approvals remain unchanged; no admission or player load follows.
- A revocation while admission commits despite cancellation: the marker remains, no player loads and a subsequent start stays blocked pending parent recovery.

The existing app-container constructor still wires production dependencies. An internal constructor exposes those same dependencies to tests; no service locator, new production service or runtime library was added. The coroutine-test dependency was already used by the core tests and is now reused by app tests.

These tests do not use Room, a WebView or a physical device. They do not establish Room invalidation latency, instantaneous atomic enforcement at the moment a database row changes, or preservation of real elapsed watch time under storage failure. Repeat approval/source changes during real playback and at admission/write boundaries on a device before closing this release gate. Unrelated authorization emissions may conservatively stop playback; no claim of uninterrupted playback across parent edits is made.

## Real Room observer follow-up

Two additional `AuthorizationFlowTest` cases use real file-backed Room databases under Robolectric and keep one `WhitelistRepository.observeVisible` collector alive across each sequence. The first verifies that revoking another queued video and deleting the remaining video produce updated visible lists. The second blocks and reallows a source: approved content disappears/reappears while explicitly rejected content stays rejected. Tests cancel/join the collector and close the database before deleting only their uniquely named synthetic test database.

This adds actual Room invalidation/repository evidence to the controlled ViewModel tests above, with no new dependency or production change. It does not combine Room, ViewModel and WebView in a single end-to-end test. The ten-second receive timeout is a test hang guard, not a claimed enforcement latency or release performance target. Device-level stop/audio/network behavior and concurrent transaction/admission races remain open.
