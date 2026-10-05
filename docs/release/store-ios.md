# Apple release checklist — BLOCKER

Checked against official guidance on 2026-09-09. This is technical preparation, not legal clearance or an App Review guarantee.

- [ ] Decide target audience, age rating and whether Kids Category distribution is appropriate. Obtain a documented assessment of third-party video services in that context.
- [ ] Validate YouTube child-directed designation, player requirements and every path to provider-controlled content; do not implement policy circumvention.
- [ ] Publish approved privacy and support URLs with the actual operator/contact. The repository privacy policy is a draft.
- [ ] Complete App Privacy declarations from physical-device network observations, including WebKit and provider behavior, not just the app's own requests.
- [ ] Inspect the packaged PrivacyInfo.xcprivacy, each Required Reason API and actual use. Presence of a manifest alone is not compliance.
- [ ] Review ATS, entitlements, URL/deep-link handlers, permissions and background modes in the final archived product.
- [ ] Configure distribution signing and provisioning outside Git; validate an archive and TestFlight build. Local unsigned Release build success is insufficient.
- [ ] Test parental gates, offline/error states, deleted/region-restricted/age-restricted videos, player shutdown and persistence recovery on physical devices.
- [ ] Complete the [minimum-OS and installed-app upgrade matrix](ios-compatibility.md); passing schema tests on current simulators do not certify iOS 17 or signed upgrades.
- [ ] Complete VoiceOver, large text, contrast, Reduced Motion and Dark Mode checks.
- [ ] Supply reviewed screenshots, icon rights, localization, review instructions and applicable content rights.
- [ ] Complete energy gates and obtain explicit store submission approval.

Sources: [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/), [privacy manifests](https://developer.apple.com/documentation/bundleresources/privacy_manifest_files), [App Privacy details](https://developer.apple.com/app-store/app-privacy-details/), [YouTube developer policies](https://developers.google.com/youtube/terms/developer-policies).
