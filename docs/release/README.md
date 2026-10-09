# Release preparation

This is a prerelease workspace, not an approved release candidate. Start with [the audit](pre-release-audit.md), [repository provenance](repository-audit.md), and [feature parity](feature-parity.md).

## Version and builds

`version.properties` is the product version source. Keep 0.1.0 until release gates pass. iOS includes this file through its xcconfig; Android reads it in Gradle. Store build numbers must increase independently when uploading a new build. Never reset an already published build number.

Use Xcode 26.6 and XcodeGen for iOS; use JDK 17, Android SDK 36 and the checksum-pinned Gradle wrapper for Android. See the platform READMEs. Generation and packaging must include exactly the six entries of `content/public-files.txt`; private libraries must never enter application bundles. Run `node scripts/validate-content.mjs` and `node --test scripts/player-lifecycle.test.mjs`.

Local verification commands:

```sh
cd android
./gradlew assembleDebug assembleRelease bundleRelease testDebugUnitTest lintDebug lintRelease
```

```sh
cd ios
xcodegen generate
xcodebuild test -project sidetube.xcodeproj -scheme sidetube -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:sidetubeTests CODE_SIGN_IDENTITY=-
xcodebuild build -project sidetube.xcodeproj -scheme sidetube -configuration Release -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO
```

UI tests using the Keychain need an ad-hoc signed simulator build, not a signing-disabled build. Choose an isolated simulator and run the parent PIN, bedtime and sleep-mode flows. Android still needs device/instrumentation coverage; local JVM tests are not a substitute.

See the [iOS compatibility and upgrade matrix](ios-compatibility.md) for runtime-specific evidence and the remaining minimum-OS, signed-update and interrupted-session gates.

The CI workflow performs native builds, unit tests, Android lint, content checks, JavaScript lifecycle checks and history/object/worktree secret scanning. It has not been run on GitHub. Hosted runner and XcodeGen changes mean bit-for-bit reproducibility has not been established.

## Publication sequence

1. Resolve every BLOCKER in the audit and obtain product-owner approval of the target audience and provider model.
2. Verify licensing, notices, asset/content permissions and publishable author metadata.
3. Work through the [device and owner checklist](device-checklist.md) on physical hardware; attach sanitized evidence.
4. Freeze versions and lock dependency inputs; run both platform checklists and full CI.
5. Scan all final refs, objects and the working tree with redaction. Review scanner exclusions. Recheck dependency vulnerabilities at the frozen version.
6. Preserve verified offline backups before any history migration. Retain fork attribution and meaningful development history. A mirror does not include inaccessible server-side reflogs, issues or unadvertised objects.
7. Obtain explicit permission before rewriting public history, pushing tags, publishing releases or uploading to stores.
8. Sign through locally configured secure credentials, outside Git. Record artifact hashes and test evidence for the exact signed artifacts before declaring a release candidate.

Do not push audit/backup refs containing unreviewed private material. Secret deletion does not revoke credentials: any confirmed credential requires owner-led revocation/rotation before history cleanup.

See [Apple checklist](store-ios.md), [Google Play checklist](store-android.md), [energy gates](../energy/README.md), and [accessibility gates](../accessibility/README.md).
