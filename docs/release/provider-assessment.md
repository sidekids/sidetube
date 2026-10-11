# Provider and store assessment (2026-10-11)

Desk assessment of SideTube against the rules that apply once it is distributed publicly ([ADR 0009](../adr/0009-oeffentliche-fassung-kanal.md)). It compares the current code with the primary sources below. **It is not legal advice**; items marked *decision* need the owner, items marked *fix* are concrete code or text changes. Owner checklist items: O1 (provider model), O4 (store disclosures).

Sources, read on 2026-10-11:
- YouTube API Services – Developer Policies, last updated 2026-09-14 (`developers.google.com/youtube/terms/developer-policies`)
- YouTube API Services – Required Minimum Functionality, last updated 2026-09-14 (`…/required-minimum-functionality`)
- Google Play – Families Policy (`support.google.com/googleplay/android-developer/answer/9893335`, no date shown)
- Apple – App Review Guidelines 1.3, 4.2, 5.1.4, 5.2.2, 5.2.3 (`developer.apple.com/app-store/review/guidelines/`)

## Summary

| # | Topic | Rule | SideTube today | Verdict |
|---|---|---|---|---|
| 1 | Overlays on the player | RMF: no overlays, frames or visual elements in front of any part of an embedded player | Fixed 2026-10-11: iOS full screen no longer draws anything over the video – controls and status below it, the „Fertig“ panel beside it, „Verkleinern“ in the control row; a UI test asserts no overlap. Android and iOS portrait already placed controls below the player | **done** (landscape layout only seen in code; the simulator does not rotate in UI tests) |
| 2 | Hidden controls (`controls: 0`) | RMF: only changes described in the API documentation | `controls` is a documented IFrame player parameter | Compliant as far as the text goes; keep the player ≥ 200×200 px |
| 3 | Blocking player functionality | Dev. Policies I.6: must not modify or block any portion or functionality of the player; III.F.2.c: must not obscure YouTube attribution | Both apps cancel navigation out of the embed (logo, title, end-screen links) and stop videos that YouTube loads on its own | **Deferred 2026-10-11 ([ADR 0011](../adr/0011-store-einordnung.md))** until device evidence is in; must be decided before any store submission. The core child-safety promise collides with I.6. Options: keep and ask YouTube for written approval; or let such taps open nothing but a parent-gated notice. Attribution itself (logo, title bar) stays visible |
| 4 | Links to YouTube ToS and Google privacy policy | III.A: show a link to the YouTube Terms; users agree to a privacy policy that links the Google Privacy Policy, shown prominently at all times | Links exist only in `docs/privacy-policy.md`, nowhere in the apps | **Done in the apps 2026-10-11:** Android „Rechtliches“ in the parent menu, iOS „Über SideTube“, both with SideTube privacy policy, YouTube Terms and Google Privacy Policy; first-run PIN setup states that these apply (iOS with links). **Drafted 2026-10-11** in the website repository (Gitea SideKids/WWW, not yet live): the public SideTube privacy section now covers both platforms, wishes, the Nextcloud notification, camera, per-platform cookie handling, links to the Google Privacy Policy and YouTube Terms, the responsible person and user rights |
| 5 | Storage of YouTube data | III.E.4: non-authorized data at most 30 days, then delete or refresh; keep consistent | Titles, channel names and thumbnail URLs of approved items and the channel cache are kept indefinitely; no refresh | **Done 2026-10-11 ([ADR 0010](../adr/0010-youtube-daten-30-tage.md)):** both apps refresh item metadata older than 30 days once a day (oEmbed/channel page, ≤ 25 per run), send vanished items back to review, delete cache, watch history and decided wishes older than 30 days, strip YouTube titles from older open wishes. Android Room 3→4 and iOS lightweight migration tested |
| 6 | Child-directed clients | III.J: comply with COPPA/GDPR, no personalised ads, no write actions; III.E.4.j: turn tracking off for Made-for-Kids videos | No write actions, no ads, no own tracking; `youtube-nocookie` host; Android rejects third-party cookies | Largely compliant; the „made for kids“ status of each video is not checked |
| 7 | Apple Kids Category | 1.3: no data to third parties, no third-party analytics/ads, no links out except behind a parental gate | The embedded YouTube player sends device and network information to Google | **Decided 2026-10-11 (ADR 0011):** a regular category aimed at parents, not the Kids Category; replaces the owner decision of 2026-08-31 |
| 8 | Apple wrappers and third-party services | 4.2/4.2.2: more than a repackaged site; 5.2.2: permitted under the service's terms, proof on request; 5.2.3: no downloading | SideTube adds curation, profiles, time rules, wishes; streams only, no download | Plausible; 5.2.2 depends on items 1, 3 and 4 |
| 9 | Google Play Families | Child-directed apps: only approved SDKs, no identifiers, no mere webview of a site; mixed audience needs a neutral age screen | No ad SDKs, no identifiers, no location; WebView shows the official embed only | **Decided 2026-10-11 (ADR 0011):** target audience „parents and children“ (mixed) |

## What this means for the order of work

1. Before the store phase, decide items 3, 7 and 9 together – they define what SideTube may promise and in which category it is listed.
2. Items 1, 4 and 5 are code and text work that is needed in any case and does not depend on those decisions.
3. Item 6 (Made-for-Kids check) is optional hardening; YouTube itself already limits tracking on such videos.

The GitHub pre-release channel is not a store; the Developer Policies apply to it all the same, because they govern the API client, not the distribution channel.
