# Architecture

SideTube remains one monorepo with two native applications. `content/` owns curation definitions; `content/public-files.txt` is the public bundle boundary. `version.properties` owns the common product version and independent monotonic platform build numbers. `branding/` owns visual identity. Security, privacy and release specifications live under root documentation, not duplicated in platform READMEs.

iOS uses SwiftUI screens, observable session/player models, SwiftData repositories, URLSession for metadata and WKWebView for YouTube/PeerTube. UIKit/WebKit adapters host the web players. The current project uses Swift 5 mode and MainActor-default settings. SwiftData and the Keychain persist different kinds of data; see the storage inventory.

Android uses Compose, ViewModels with StateFlow, manually constructed AppContainer services, Room, and a small HttpURLConnection abstraction. `core` is an Android library because it contains Room, although much of its logic is platform-independent. The player WebView is attached to the Activity window to preserve the documented Sidephone rendering workaround. Playback teardown removes it and cancels its deferred retry. Flow collection in Compose follows lifecycle; repeated parent/profile subscriptions are cancelled when replaced.

The central content flow is: parent supplies URL → provider resolves metadata → curation creates a review/rejected item → parent decision → local storage → child visibility policy → playback. Trusted-channel browsing is a separate broader authorization and requires further consistency testing. Automatic screening may reject or flag; it is not a safety certificate. iOS's former automatic starter approvals migrate to pending review.

Providers receive metadata requests and media playback traffic. Runtime networking is not limited to URLs visible in native source, because remote WebView code issues its own requests. No SideTube server is introduced. Both players are intended to exist only while used; child-session deadlines, image tasks and provider process behavior still need device measurements.

Do not move directories merely to match a template: existing `content/` and `branding/` paths already express shared ownership. Do not introduce a UI-sharing framework or copy licensing-incompatible code to obtain parity.

Interaction and visual semantics on the Sidephone (focus, ring keys, virtual wheel, accent colour) follow SideUI as a written specification, not as shared code; see [design/sideui.md](../design/sideui.md).
