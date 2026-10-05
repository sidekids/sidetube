# Android build

Native Kotlin/Compose app; Room persistence in `core`, platform UI and WebView in `app`. This is the replacement Android client, not the historical GPL fork. See the root [README](../README.md) for product, privacy, licensing and shared policy.

Use JDK 17 and Android SDK platform 36. Set `ANDROID_HOME` or an ignored `local.properties` containing `sdk.dir`.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew assembleRelease bundleRelease lintRelease
```

Debug uses the `.debug` application-ID suffix. Release output is unsigned; signing and upload require separate credentials and approval. No production signing key is included.

The build copies only `../content/public-files.txt` entries to generated assets. Core tests consume the same content. Never place local private libraries in `app/src/main/assets/content`.

The current code resolves YouTube links with oEmbed, channel pages and RSS. The optional `YOUTUBE_API_KEY` build setting is not wired to a Data API client in this replacement; setting it does not provide search capabilities. No Google login.

[Parity gaps and release limitations](../docs/release/feature-parity.md) include time-limit durability/device validation, the missing full-screen child overlay, PeerTube and localization. Daily budgets, quiet hours and the sleep timer now gate playback using cancellable deadlines; this is not yet a complete device-validated parental-control system. See [time-control verification](../docs/release/android-time-control.md). Hardware-key interception and the Activity-attached WebView need testing on the Sidephone as well as conventional phones.
