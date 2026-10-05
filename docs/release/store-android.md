# Google Play release checklist — BLOCKER

Checked against official guidance on 2026-09-09. This is technical preparation, not legal clearance or an acceptance guarantee.

- [x] Set compileSdk and targetSdk to 36; locally build the release APK/AAB with AGP 8.9.3 and Gradle 8.11.1.
- [ ] Verify target requirements again at submission. Since 2026-08-31, new mobile apps and updates must target API 36 (subject to documented exceptions/extensions).
- [ ] Decide target audience and complete Families Policy assessment, including third-party WebView/player services. Persistent and third-party cookies remain an open privacy issue.
- [ ] Close unintended navigation/foreign-video paths; implement and test protective controls currently absent from the Android rewrite before claiming iOS parity. Subframe navigation and unsolicited-video guards are now implemented and unit-tested (2026-09-10); real click-through/device verification is still outstanding.
- [ ] Complete Data Safety from observed app, WebView and provider behavior; publish an approved privacy policy and operator/support contact.
- [ ] Review merged manifest permissions, exported components, intent handlers, backups and network-security settings in the final artifact.
- [ ] Configure Play App Signing and an upload key outside Git. Verify signing and versionCode uniqueness; the local release APK is unsigned.
- [ ] Test Android 16 behavior, supported older versions, TV/remote behavior where supported, and WebView variations on devices.
- [ ] Complete offline/error-state, parental gate, persistence, player-lifecycle, TalkBack and energy tests.
- [ ] Review content rating, store listings, localization, screenshots, branding and media rights.
- [ ] Run internal testing and the Play pre-launch report; obtain explicit upload/publication approval.

Sources: [target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en), [Families Policy](https://support.google.com/googleplay/android-developer/answer/9893335?hl=en), [Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), [AGP compatibility](https://developer.android.com/build/releases/about-agp).
