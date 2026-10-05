# Third-party notices and provenance

The current `ios/LICENSE` and `android/LICENSE` declare Mozilla Public License 2.0. Their notices must remain with covered source. This file supplements those licenses; it does not relicense earlier code, shared content or branding.

The publicly published earlier Android client derives from [Péter Dégi's YouTubeWhitelist](https://github.com/degipe/YouTubeWhitelist), under GPL-3.0 as declared by that historical project. Preserve its copyright, license, corresponding-source and attribution obligations for every distribution of that fork. A later MPL declaration does not cancel earlier GPL obligations. Do not copy historical GPL implementation into the replacement under MPL alone.

The developer snapshot states that Android was independently rewritten using iOS behavior as its specification. The preserved standalone Android repository contains 16 incremental commits, including domain/storage/PIN, providers, curation, parent UI, navigation and player phases. This is useful provenance evidence, but does not independently certify that all expressive code/assets are owned or compatibly licensed. The owner must validate the provenance statement before public migration. Original repositories and authorship remain backed up, including development plans withheld from the release tree.

Android's runtime includes AndroidX (including CameraX for the optional QR scan of the parent-notification setup code), ZXing core (Apache-2.0, QR decoding), Jetpack Compose, Room, Kotlin standard libraries, kotlinx coroutines/serialization, Tink and related transitive components. The [resolved inventory](docs/licenses/android-runtime.md) lists exact coordinates and primary license metadata. Most declare Apache-2.0; other declared terms are listed individually. Build/test tools include Gradle/AGP, KSP, JUnit 4, Truth and Robolectric. Error Prone and JSR-305 annotations are compile-only inputs for R8, not new runtime features. The Gradle wrapper retains its original Apache notice.

iOS currently declares no third-party Swift Package/CocoaPods/Carthage dependency. It uses Apple system frameworks and the remotely served YouTube/PeerTube player. XcodeGen is a build tool, not an embedded runtime dependency. Remote player terms remain applicable even though their code is not a packaged native SDK.

`content/` and `docs/` have the separate reuse restrictions stated in root `LICENSE`. Do not describe the whole repository as uniformly open-source under MPL. Branding rights are reserved. Video metadata, thumbnails, provider logos, screenshots and included artwork require provenance/usage-right review; a video's public availability does not grant redistribution rights. A complete store-asset rights inventory remains a release gate.

Reference: [MPL text](https://www.mozilla.org/en-US/MPL/2.0/), [MPL FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/). No legal clearance is inferred merely from matching license filenames or an empty scanner report.
