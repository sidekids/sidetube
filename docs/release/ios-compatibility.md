# iOS compatibility and upgrade evidence

The deployment target is iOS 17.0. Building with that target does not establish runtime compatibility. Record the exact OS and app revision for each test; successful simulator checks do not certify physical-device behavior or signed app updates.

## Upgrade cases required before release

- Create synthetic profiles, distinct approved/review-required/rejected items and known watch time in the old app. Never use a real child's store as a public fixture.
- Update the installed app without uninstalling or clearing its container. Verify profile identity, parent PIN, policy settings, approval relationships and recorded time survive.
- For the pre-admission-schema transition, verify no marker is fabricated for an idle profile and that a new session can create and cleanly remove a marker after upgrade.
- Test an interrupted session separately: terminate during playback, relaunch without resetting storage, verify wrong/cancelled PIN cannot clear the marker, then confirm recovery preserves recorded time.
- Run on the minimum supported OS and representative current OS versions. Include stores created by an older OS and then opened after an OS/app update.
- Retain failed attempts and sanitized evidence. Restore test-device settings after clock/time-zone scenarios; do not change the developer's primary device.

## Behavior changes after an update (for the release notes)

Changes an existing family notices without doing anything; the release notes of the first build containing them must say so.

- **Virtual wheel off after the update.** The on-screen scroll wheel („Fernbedienung mit Scrollrad“) is now a per-profile parent setting and off by default (SideUI ADR 0008–0010, commit `4cc44d9`). Existing profiles are migrated with the default, so a child who used the wheel before finds it gone. Suggested note: „Das Scrollrad ist jetzt je Kind einstellbar und nach dem Update zunächst aus. Einschalten in den Einstellungen: Profil → Fernbedienung mit Scrollrad.“
- **Playlist videos from channels with „Nur einzeln geprüfte Videos“ need their own approval** ([ADR 0002](../adr/0002-playlist-videos-und-einzelpruefung.md)); they disappear from approved playlists until approved individually. Both platforms.
- **Cached playlist entries from before the update** carry no channel ID; offline, such a video plays only with its own approval until the playlist has been opened once with network. Both platforms.

## Automated scope

Verified on 2026-09-10 with Xcode 26.6 and application/test revision `723e78b`:

| Runtime | Unit tests including frozen-schema migration | Scope |
|---|---|---|
| iOS 26.5 (23F77) | PASS: 183 tests, 41 suites | Dedicated iPhone 17 simulator |
| iOS 26.4 (23E244) | PASS: 183 tests, 41 suites | Separate fresh iPhone 17 simulator |
| iOS 17 minimum | NOT RUN | No local runtime available; remains BLOCKER |

The same revision also passes all three selected parent UI flows on iOS 26.4: bedtime/PIN (25.0 s), lockout expiry/correct PIN (61.0 s), and sleep overlay/PIN (31.5 s). These are XCTest execution durations, not application performance measurements. Ad-hoc signing was enabled. Private logs: `ios-26_4-migration-unit.log` and `ios-26_4-parent-ui.log`. The dedicated simulator is shut down after verification; no device data is deleted.

`preAdmissionSchemaMigratesProfilesApprovalsAndHistory` creates a store with the six frozen models from `7898667`, then opens it with the seven current models. It checks model membership and retained synthetic data. This is a same-process schema migration on the selected runtime, not an installation or cross-OS upgrade.

`diskMarkerAndParentRecoverySurviveSeparateContainerLifetimes` checks durable marker/recovery state using three container lifetimes against the same file. It does not terminate the app process.

The selected UI flows are `testBedtimeBlocksKidModeUntilParentPIN`, `testSleepModeOverlayAndParentPIN`, and `testPINLockoutExpiresAndAllowsCorrectPIN`. They do not test interrupted-session recovery.

### Interrupted-session UI test

PASS on 2026-09-10: isolated iPhone 17 simulator, iOS 26.5, Xcode 26.6, ad-hoc signed Debug app. One UI test passed in 49.2 s (test execution time, not a performance measurement). Private evidence: `ios-recovery-relaunch-ui.log`. The unsigned device-target Release build also passes; the new fixture flag, placeholder title and fixture failure message are absent from its executable.

`testInterruptedSessionSurvivesRelaunchAndRequiresParentPIN` uses a synthetic profile, approved placeholder video, 30 seconds of history and a pending marker. Fixture creation is compiled only for Debug simulator builds and requires both `sidetube.uiTestReset` and `sidetube.uiTestInterruptedSession`; ordinary launches, physical-device builds and Release builds cannot create it. After the first launch the test removes reset, fixture and PIN arguments, terminates the app and relaunches it using persisted state.

The assertions cover blocked playback after relaunch, wrong-PIN and cancellation behavior, successful PIN acknowledgement without automatic playback, and the ability to open a new player after another process restart. The final explicit play attempt may contact the video provider for the synthetic placeholder ID; successful provider playback is not required or claimed. Recorded-time preservation is covered by repository tests, not by reading the history through this UI test.

This is an actual app-process relaunch with a **prepared interrupted state**, not a forced kill while media is running. Kill timing during admission/history writes, disk-full failures, physical devices and signed updates remain separate gates.

## Reproduction

Use an isolated simulator whose runtime is explicitly selected, not the first device with a matching name. List available runtimes/devices with `xcrun simctl list`; obtain missing minimum-OS infrastructure before claiming the gate passes. Generate the Xcode project as described in the platform README. Run the entire `sidetubeTests` target and the three UI methods above with `-destination 'platform=iOS Simulator,id=<isolated-device-UUID>'` and `CODE_SIGN_IDENTITY=-`. The PIN UI tests need ad-hoc signing for Keychain access; do not disable signing. Preserve the result bundle and log outside the publishable tree, and shut down the dedicated simulator afterward.

The minimum-supported-OS, real process-termination and signed-install upgrade gates remain **BLOCKER** until executed successfully.
