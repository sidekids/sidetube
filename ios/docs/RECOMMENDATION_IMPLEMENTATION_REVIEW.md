# SideTube recommendation implementation review

> Historisches Dokument: Aussagen und Implementierungspfade können den früheren Stand beschreiben. Maßgeblich sind der [aktuelle Release-Audit](../../docs/release/pre-release-audit.md) und seine offenen Gates; dieses Dokument ist keine Release-Freigabe.

## Audit scope

This checkout is the native iOS SwiftUI/SwiftData application (`Sources/`). It does
not contain an `android/` module, so Android cannot be changed from this checkout.
The root README describes a larger monorepo, but the checked-out branch is the iOS
project (`project.yml`, `sidetube.xcodeproj`).

Relevant iOS paths:

- `Sources/Domain/ContentPolicy.swift`: the central visibility decision.
- `Sources/Domain/Curation.swift`: approval status and source trust levels.
- `Sources/Data/WhitelistRepository.swift`: profile-scoped visible content.
- `Sources/Features/Kid/KidModels.swift`: curated home, channel, playlist and search rows.
- `Sources/Features/Kid/PlayerCoordinator.swift`: creates the in-app playback queue.
- `Sources/Features/Player/PlayerModel.swift`: playback state, history and time accounting.
- `Sources/Features/Kid/PlayerScreen.swift`: existing “Als Nächstes” queue UI.
- `Sources/Data/ChannelVideoCacheRepository.swift`: cached trusted-channel candidates.

## Existing policy findings

`ContentPolicy.evaluate` already rejects blocked/parent-only sources, all statuses
other than `approved`, age mismatches, live videos, disallowed Shorts, disabled
categories, sensitive news and sexual content. `SourceTrust.allowsChannelBrowsing`
is deliberately true only for `trustedChildSource`. Automatic risk screening is used
when discovering content and never sets `approved`.

The channel screen intentionally supports dynamic browsing for a trusted child source.
Those cached videos are not profile whitelist approvals; they are permitted only by the
existing source-level browsing rule and still need the same age/category/risk checks.

The player previously trusted its caller when constructing a queue. That is unsafe for
recommendations: a future callback or external candidate could call `load` without a
policy decision. The coordinator is therefore now a second enforcement boundary, and
recommendation taps go through the same boundary.

## Options

### A — intercept embedded YouTube related-video taps

This is not sufficiently controllable. YouTube’s embedded related-video surface cannot
be fully removed, and a player callback is too late unless every callback is converted
into a SideTube navigation request. It also provides poor metadata for policy checks.

### B — SideTube-owned recommendation list

This is the safest and most maintainable option. SideTube owns the rows, can render
only policy-approved or explicitly trusted-browse candidates, avoids external URLs,
and can keep playlist/context ordering without engagement signals.

### C — hybrid

Use B as the child-facing UI and treat provider-related data only as an optional future
candidate source. Every candidate must be resolved into SideTube metadata and pass the
existing policy before it can be shown. Embedded-player related videos remain a
defence-in-depth limitation, never a navigation path.

## Decision

Implement B now, with the C boundary documented for a future provider adapter. The
existing player queue is reused as context (“Als Nächstes”), followed by safe profile
content and trusted-child-source cache content. Sorting is deterministic and based on
playlist/context, same channel, category and recent approval—not views, popularity,
watch time or click-through rate.

Every visible recommendation and every queue load is policy checked. Tapping a
recommendation navigates to the existing in-app player model; it never opens Safari,
the YouTube app or a normal YouTube page.

## Android parity note

No Android sources are present in this checkout. The Android implementation should
mirror the domain contract with Kotlin/Room/Hilt/Compose once that module is available:
candidate provider → metadata resolution → existing policy → deterministic ordering →
in-app player navigation. It must not introduce a second approval engine.

## Known limits

There is no recommendation API in the current checkout. Provider-related candidates are
therefore not used directly. Playlist order is preserved when the caller supplies a
playlist/list queue; trusted channel cache entries remain subject to source, age and
risk checks. YouTube embed related videos can still exist inside the provider surface,
but they are not exposed by SideTube navigation and autoplay remains governed by the
existing player settings.
