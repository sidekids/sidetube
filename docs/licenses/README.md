# License and dependency audit

See root `LICENSE`, platform LICENSE files, `THIRD_PARTY_NOTICES.md` and the [Android runtime inventory](android-runtime.md) (103 coordinates from the 2026-09-09 build plus 5 direct CameraX/ZXing additions on 2026-10-05). Native iOS declares no third-party package dependency; build tooling and remote providers still carry their own terms.

| Direct dependency / tool | Purpose | Declared license family | Maintenance / necessity assessment |
|---|---|---|---|
| Android Gradle Plugin 8.9.3 / Gradle 8.11.1 | API 36 builds | Apache-2.0 | Needed; deliberately bounded upgrade compatible with current Kotlin/build system |
| Kotlin 2.1.0 and Compose plugin | Native language/UI compilation | Apache-2.0 | Needed; exact pin older than current releases |
| AndroidX core 1.15.0 | Platform compatibility | Apache-2.0 | Current source uses it |
| Lifecycle 2.8.7 | ViewModels and lifecycle-aware collection | Apache-2.0 | Needed; runtime-compose explicitly added for lifecycle-bound UI collection |
| Activity Compose 1.9.3 | Activity/Compose integration | Apache-2.0 | Needed; RestrictedApi lint suppression is limited to the public Activity key hook |
| Compose BOM 2025.01.01, UI, Material3/icons | Native UI | Apache-2.0 | Used; icon set/minified size should be measured before removal |
| Room 2.7.0 / KSP 2.1.0-1.0.29 | Persistence and generated adapters | Apache-2.0 | Needed; migration/upgrade validation remains |
| Serialization 1.7.3 | Shared JSON and provider data | Apache-2.0 | Needed |
| Coroutines 1.9.0 | Async/Flow | Apache-2.0 | Needed; HTTP cancellation is not yet immediate |
| Security Crypto 1.1.0-alpha06 / Tink 1.8.0 | Encrypted PIN preferences | Apache-2.0 | Older prerelease is a maintenance concern; migration must preserve existing encrypted preferences |
| Error Prone annotations 2.36.0 / JSR-305 3.0.2 | Resolve annotation classes during R8 | Apache-2.0 / BSD-style | Compile-only, added after observed Release shrinker failure |
| JUnit 4.13.2, Truth 1.4.4, Robolectric 4.14.1, AndroidX test | Tests | EPL-1.0 / Apache-2.0 / MIT / Apache-2.0 | Test-only; build/test graph not covered by runtime OSV batch |
| XcodeGen | Generate native Xcode project | MIT | Build-only; CI install currently not pinned to an exact artifact |
| Gitleaks 8.30.1 | Secret scanning | MIT | Audit/CI tool; does not establish absence of all sensitive data |

OSV was queried for every resolved runtime coordinate, with no matches. This does not cover the operating system, WebView, remote provider scripts, all build/test transitive dependencies, or undisclosed vulnerabilities. Runtime license names were found in 102 POMs; the remaining Guava dependency inherits Apache-2.0 from its verified parent POM. No unnecessary analytics/advertising SDK was found or removed.

Unresolved gates: validate independent Android authorship; inventory and clear visual/store assets; retain all applicable binary notices; assess the prerelease encryption dependency and full build/test graph. The original source snapshots and private author metadata are preserved, not rewritten.
