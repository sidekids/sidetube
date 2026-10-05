# SideTube

SideTube is a video app for families with native Swift/SwiftUI and Kotlin/Compose clients in one monorepo. Parents curate sources and approve videos; local profiles and approval records are stored on the device.

**Pre-release: not ready for worldwide distribution.** See [release audit](docs/release/pre-release-audit.md) and [feature matrix](docs/release/feature-parity.md). Android is a replacement implementation with significant parity gaps.

## Repository

`ios/` contains SwiftUI, SwiftData and WebKit code. `android/` contains Compose and Room code in two modules, `app` and `core`. `content/` is authoritative for shared curation data; `content/public-files.txt` defines exactly what may be bundled. `branding/` contains the shared identity. `docs/` and `scripts/` contain specifications and validation.

The developer snapshot is the consolidation baseline. GitHub is the intended public canonical repository after audit and explicit migration approval. Both original histories are preserved privately; no migration or public history rewrite has been performed. See [repository audit](docs/release/repository-audit.md).

## Build and test

iOS: Xcode 26, XcodeGen, iOS 17 or later. Android: JDK 17, Android SDK 36; minimum Android 8/API 26. Node 22 or later runs shared player tests.

```sh
cd ios
xcodegen generate
xcodebuild test -project sidetube.xcodeproj -scheme sidetube -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:sidetubeTests CODE_SIGNING_ALLOWED=NO
```

```sh
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug
```

From the root, run `node --test scripts/player-lifecycle.test.mjs`. See [iOS](ios/README.md), [Android](android/README.md) and [release instructions](docs/release/README.md). Builds generate content resources from the public manifest; generated copies are not source files.

`version.properties` owns the product version and platform build numbers. The current development version remains 0.1.0; 1.0.0 must not be declared released before the gates pass. Signing credentials stay local. iOS's optional YouTube Data API key belongs in `ios/Config/Secrets.xcconfig`; a distributed app cannot keep an embedded API key secret.

## Privacy and child safety

There is no SideTube backend, account service, analytics SDK or crash-upload service in the reviewed runtime code. Video providers nevertheless receive network data such as IP address, requested media, client information and playback context. YouTube embeds are third-party code and may contact additional services. Privacy-enhanced mode does not establish absence of tracking or cross-session correlation.

iOS uses nonpersistent WebKit storage. Android now embeds through the same privacy-enhanced host and clears the player cookie store at session start and teardown, but still accepts third-party cookies, and neither change has been verified on a device. The apps therefore do **not** have identical privacy behavior. Read the [privacy model](docs/privacy/privacy-model.md) and [network inventory](docs/privacy/network-services.md).

Parents must approve new items, including iOS starter suggestions. Trusted-channel browsing authorizes a broader source, not individual review of each future upload. Embedded provider controls/navigation and recommendations still require real-device validation and provider-policy review. Do not rely on this prerelease as a closed child-safe playback environment.

## Optional parent notification

Parents can be told when a child makes a new wish ([ADR 0005](docs/adr/0005-eltern-ueber-wuensche-benachrichtigen.md)). It needs the family's own Nextcloud with Talk; there is no SideTube server. On the Nextcloud host, `scripts/talk-wunschkanal.sh` creates a send-only bot and a conversation, sends a test message and prints a setup code as QR code. In the app, behind the PIN: settings → „Eltern benachrichtigen“ → scan or paste the code → „Test senden“. The parents' phones only need the regular Nextcloud app. Messages contain the parents' mentions and the number of open wishes – no child name, topic or title. See the [implementation plan](docs/architecture/eltern-benachrichtigen.md) and the [privacy notice draft](docs/privacy-policy.md).

## Licensing and contributing

[LICENSE](LICENSE) describes the current snapshot: native clients declare MPL-2.0; content/documentation have restricted reuse and branding rights are reserved. The historical Android client was a GPL fork of [Péter Dégi's YouTubeWhitelist](https://github.com/degipe/YouTubeWhitelist). The replacement's independent provenance remains a release gate; historical GPL obligations and attribution cannot be removed by a new root commit. See [third-party notices](THIRD_PARTY_NOTICES.md).

**Trademarks and content:** YouTube, PeerTube and the names of broadcasters, channels and organisations (e.g. KiKA, WDR, NASA, ESA) belong to their owners. SideKids is not affiliated with or endorsed by them. The lists under `content/` refer to third-party channels and videos by identifier only; their media is not part of this repository.

Product concept and curation: Christian-Maximilian Steier / SideKids.

Read [CONTRIBUTING](CONTRIBUTING.md), [SECURITY](SECURITY.md) and the [architecture](docs/architecture/README.md). Contributions should be small, native, tested and consistent with shared policy.
