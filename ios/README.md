# iOS build

Native SwiftUI, SwiftData and WebKit, iOS 17+. Xcode 26 and XcodeGen are required. The project currently uses Swift 5 language mode with approachable concurrency and default MainActor isolation; do not describe that as verified Swift 6 strict-concurrency compliance.

Run `xcodegen generate` from this directory. The pre-generation step prepares the reviewed public content; repeat generation after changing content or version settings. `project.yml` is authoritative; generated Xcode projects are ignored.

Run unit tests with `xcodebuild test -project sidetube.xcodeproj -scheme sidetube -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:sidetubeTests CODE_SIGNING_ALLOWED=NO`. Select an installed simulator if that name is unavailable. Use `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` per command if the system currently selects Command Line Tools.

For an unsigned device build use `xcodebuild build -project sidetube.xcodeproj -scheme sidetube -configuration Release -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO`. Distribution archives require the owner's team and signing setup, supplied locally; the project no longer hardcodes a developer account.

The optional API key belongs in ignored `Config/Secrets.xcconfig`. Shared version settings are loaded from `../version.properties`. Never commit profiles, certificates, archives, personal Xcode settings or diagnostics.

Live network tests are opt-in (`TEST_RUNNER_SIDETUBE_LIVE=1`) and are distinct from deterministic unit tests. Existing UI tests include live provider requests. See the root release report for actual results and remaining tests.
