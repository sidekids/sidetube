# SideTube

🇩🇪 Deutsche Fassung: [README.de.md](README.de.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platforms](https://img.shields.io/badge/platforms-iOS%2017%2B%20%7C%20Android%208%2B-blue.svg)](#repository-layout)

SideTube is a video app for children that shows only what a parent has approved
beforehand. There is no account, no recommendation feed and no open search. It exists
for iOS and Android; both apps share the same curation data and the same rules.

> **Scope.** SideTube constrains what a child can reach inside the app. It is not a
> substitute for accompanying a child, and it makes no claim to filter the open web.
> Playback uses the embedded YouTube player, whose limits are stated plainly under
> [Known limits](#known-limits).

## Overview

Parents add channels, videos and playlists one at a time. Nothing a child sees arrives
there by itself: every new item enters a review queue as *review required*, and only an
explicit approval makes it visible. Sources carry a trust level that decides whether a
whole channel may be browsed or only individually approved videos are shown. An automatic
risk screen may flag or reject an item, but it can never approve one — that judgement
stays with the parent.

The two apps do not share program code; language, UI framework, persistence and player are
platform-specific. What they share are **content, rules, brand and documentation**, kept
in `content/` as the single authoritative source.

## Features

- Approval instead of filtering: channels, videos and playlists are whitelisted per child
  profile; new items land in a review queue and never appear unreviewed.
- Trust levels per source: only a *trusted child source* may be browsed as a whole channel;
  every other level shows individually approved videos only.
- Risk screening that never approves: term-based detection of sensitive topics, matching at
  word boundaries so that *Attentäter* is not read as *Täter*.
- Age bands and content categories, with per-item minimum and maximum age.
- Time limits: daily watch budget, sleep timer with volume fade-out, and quiet hours with
  advance warning and a weekend offset — exceptions only via the parent PIN.
- Starter packs: curated libraries in `content/libraries/` that a parent can load into a
  profile; they arrive for review, not approved.
- Wheel navigation: the child mode can be operated entirely through a virtual click wheel,
  matching the hardware of the Sidephone.
- Two providers: YouTube, and PeerTube restricted to an instance allowlist.
- Entirely on-device: no backend, no analytics, no tracking.

## Repository layout

```
├── ios/          SwiftUI app (Swift 6, SwiftData, XcodeGen), iOS 17+
├── android/      Jetpack Compose app (Kotlin, Room, Hilt), Android 8+ — moving in
├── content/      shared curation data, authoritative for both apps
│   ├── schema/       categories, age bands, trust levels, approval states
│   ├── sources.json  source registry including PeerTube instances
│   ├── risk-terms.json
│   └── libraries/    starter packs
├── branding/     logo specification and asset generator
├── docs/         curation policy, interface concept, store preparation
└── scripts/      asset generation, content sync, build helpers
```

`content/` is authoritative; each platform receives a copy as a build artefact
(`scripts/sync-content.sh ios|android`). The copies are not versioned.

## Requirements

**iOS** — Xcode 26, deployment target iOS 17, [XcodeGen](https://github.com/yonaskolb/XcodeGen).
**Android** — JDK 17, Android SDK with platform 35, Gradle wrapper included.

A YouTube Data API key is **optional** on both platforms: channels and videos are resolved
through oEmbed and the channel page. A key raises the quota for search-heavy use.

## Installation

```bash
# iOS
cd ios && xcodegen generate && open sidetube.xcodeproj

# Android
cd android && ./gradlew assembleDebug
```

For an API key on iOS, create `ios/Config/Secrets.xcconfig` from the supplied example; on
Android, add `YOUTUBE_API_KEY` to `local.properties`. Neither file is versioned.

## Usage

On first start the app asks for a parent PIN. Behind it:

| Step | Where |
|---|---|
| Create a child profile | Parent area → *New profile* |
| Add a channel or video | Profile → *Add* → paste the YouTube link |
| Load a curated starter pack | Profile → ⋯ → *Load starter pack* |
| Approve pending items | Profile → *Review approvals* |
| Set trust level of a source | Parent area → ⋯ → *Sources and trust levels* |
| Time limits and quiet hours | Profile → ⋯ → *Edit profile* / *Sleep mode* |

Locking the app returns to child mode, where only approved content is reachable.

## Testing

```bash
cd ios && xcodebuild test -scheme sidetube -destination 'platform=iOS Simulator,name=iPhone 17'
cd android && ./gradlew testDebugUnitTest
```

The iOS suite covers domain rules, curation, risk screening and the parent/child flows; the
Android suite covers the same domain rules plus repositories and view models.

## Known limits

- **Related videos cannot be fully suppressed** in the embedded YouTube player. Since 2018
  `rel=0` only restricts them to the same channel. SideTube counters this with autoplay off,
  an immediate stop at the end of a video, and browsing restricted to child-oriented sources.
  For genuinely closed playback, PeerTube is the better provider.
- **PeerTube is federated**, so any instance moderates itself; only instances on the allowlist
  may deliver content.
- **iOS offers no kiosk mode** to third-party apps; the app points to Guided Access instead.
- **Risk screening is a hint, not a verdict.** An inconspicuous title can still be unsuitable.

## Privacy

All data stays on the device: profiles, whitelists, watch history and settings live in the
local database. There is no backend, no account, no analytics and no tracking. Network
requests go to the video providers only, to resolve and play the approved content. The
parent PIN is stored as a PBKDF2-HMAC-SHA256 derivation in the system keychain, never in
plain text.

## Project context

SideTube is part of **SideKids** ([sidekids.github.io](https://sidekids.github.io)), a small
family of child-centred applications built on shared principles: no engagement mechanics, no
data collection about children, offline capability wherever possible, and decisions that stay
with the parents rather than an algorithm.

The curation follows established media-pedagogical guidance for the relevant age bands
(among others the recommendations of [FLIMMO](https://www.flimmo.de)). Age bands, content
categories and trust levels are documented in `docs/` and defined in machine-readable form in
`content/schema/`, so that both apps and any later evaluation work from the same definitions.

Idea, curation and pedagogical concept: Christian-Maximilian Steier.

## License

Released under the [GNU General Public License v3.0 or later](LICENSE). As a copyleft
license, the GPL requires that redistributions and modified versions are also licensed under
the GPL. The Android app originates as a fork of
[degipe/YouTubeWhitelist](https://github.com/degipe/YouTubeWhitelist) and inherits this
license; the iOS app is an independent rewrite released under the same terms.
