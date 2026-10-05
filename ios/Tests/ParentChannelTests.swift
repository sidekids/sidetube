// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

/// Elternkanal (ADR 0005): Einrichtungscode, Nachrichtentext, Signatur, Versand.
struct ParentChannelTests {
    private let secret = String(repeating: "0123456789abcdef", count: 4)

    private func code(_ overrides: [String: Any] = [:]) -> String {
        var object: [String: Any] = ["v": 1, "art": "talk", "server": "https://wolke.example.org",
                                     "gespraech": "abcd2345", "schluessel": secret, "erwaehnen": ["anna"]]
        for (key, value) in overrides { object[key] = value }
        return String(data: try! JSONSerialization.data(withJSONObject: object), encoding: .utf8)!
    }

    @Test func validCodeIsRead() throws {
        let channel = try ParentChannel.parse(code(["server": "https://wolke.example.org/"]))
        #expect(channel.server.absoluteString == "https://wolke.example.org")
        #expect(channel.conversation == "abcd2345")
        #expect(channel.mentions == ["anna"])
        #expect(channel.messageURL.absoluteString
                == "https://wolke.example.org/ocs/v2.php/apps/spreed/api/v1/bot/abcd2345/message")
    }

    @Test func subfolderInstallationKeepsPath() throws {
        let channel = try ParentChannel.parse(code(["server": "https://example.org/nextcloud"]))
        #expect(channel.messageURL.absoluteString
                == "https://example.org/nextcloud/ocs/v2.php/apps/spreed/api/v1/bot/abcd2345/message")
    }

    @Test(arguments: [
        ["v": 2], ["art": "ntfy"], ["server": "http://wolke.example.org"], ["server": "https://user:pw@wolke.example.org"],
        ["gespraech": "../x"], ["schluessel": "kurz"], ["schluessel": String(repeating: "a", count: 129)],
        ["erwaehnen": [String]()], ["erwaehnen": ["anna\nben"]],
    ] as [[String: Any]])
    func invalidCodesAreRejected(_ override: [String: Any]) {
        #expect(throws: ParentChannelError.self) { try ParentChannel.parse(code(override)) }
    }

    @Test func garbageIsRejected() {
        #expect(throws: ParentChannelError.self) { try ParentChannel.parse("hallo") }
    }

    @Test func messageMentionsParentsWithoutChildData() {
        #expect(TalkBot.wishMessage(mentions: ["anna"], openCount: 2) == "@anna SideTube: neuer Wunsch (2 offen)")
        #expect(TalkBot.wishMessage(mentions: ["anna", "ben beispiel"], openCount: 1)
                == "@anna @\"ben beispiel\" SideTube: neuer Wunsch (1 offen)")
    }

    /// Prüfwerte gemeinsam mit Android (`TalkBotTest`), berechnet mit Python `hmac`.
    @Test func signatureMatchesSharedVectors() {
        #expect(TalkBot.signature(secret: secret, random: String(repeating: "00", count: 32),
                                  message: "@anna SideTube: neuer Wunsch (2 offen)")
                == "c39c69b26169f30a08482fe29dca8a9a17c47a618ce24d02dcdb1146a6cdcdfa")
        #expect(TalkBot.signature(secret: secret, random: String(repeating: "ff", count: 32),
                                  message: "@anna @\"ben beispiel\" SideTube: neuer Wunsch (1 offen)")
                == "caa79f952fc25b29f16781e909af5731046655d5fb134575753b56a7a9124411")
    }

    @Test func randomIsSixtyFourHexChars() {
        let random = TalkBot.randomHex()
        #expect(random.count == 64)
        #expect(random.fullyMatches("[0-9a-f]+"))
        #expect(random != TalkBot.randomHex())
    }

    @Test func notifierSendsSignedRequest() async throws {
        let channel = try ParentChannel.parse(code())
        let poster = RecordingPoster(status: 201)
        let notifier = TalkBotNotifier(channel: { channel }, poster: poster, random: { String(repeating: "00", count: 32) })
        #expect(await notifier.notifyNewWish(openCount: 2) == .sent)
        let request = try #require(poster.requests.first)
        #expect(request.url == channel.messageURL)
        #expect(request.headers["OCS-APIRequest"] == "true")
        #expect(request.headers["X-Nextcloud-Talk-Bot-Signature"]
                == "c39c69b26169f30a08482fe29dca8a9a17c47a618ce24d02dcdb1146a6cdcdfa")
        let body = try JSONSerialization.jsonObject(with: request.body) as? [String: String]
        #expect(body == ["message": "@anna SideTube: neuer Wunsch (2 offen)"])
    }

    @Test func notifierReportsFailuresAndMissingSetup() async throws {
        let channel = try ParentChannel.parse(code())
        #expect(await TalkBotNotifier(channel: { nil }, poster: RecordingPoster(status: 201)).notifyNewWish(openCount: 1)
                == .notConfigured)
        #expect(await TalkBotNotifier(channel: { channel }, poster: RecordingPoster(status: 401)).notifyNewWish(openCount: 1)
                == .failed(status: 401))
        #expect(await TalkBotNotifier(channel: { channel }, poster: RecordingPoster(error: URLError(.notConnectedToInternet)))
                    .notifyNewWish(openCount: 1) == .failed(status: nil))
    }
}

/// Merkt sich POSTs statt sie zu senden.
final class RecordingPoster: HTTPPoster, @unchecked Sendable {
    struct Request { let url: URL; let headers: [String: String]; let body: Data }
    private let lock = NSLock()
    private var recorded: [Request] = []
    private let status: Int
    private let error: Error?

    init(status: Int = 201, error: Error? = nil) {
        self.status = status
        self.error = error
    }

    var requests: [Request] { lock.withLock { recorded } }

    func post(_ url: URL, headers: [String: String], body: Data) async throws -> Int {
        lock.withLock { recorded.append(Request(url: url, headers: headers, body: body)) }
        if let error { throw error }
        return status
    }
}

/// Ablage (Phase 2) und Auslöser beim Anlegen eines Wunsches (Phase 3).
struct ParentChannelWiringTests {
    private let channel = ParentChannel(server: URL(string: "https://wolke.example.org")!, conversation: "abcd2345",
                                        secret: String(repeating: "0123456789abcdef", count: 4), mentions: ["anna"])

    @Test func keychainStoreKeepsAndDeletesChannel() {
        let store = KeychainParentChannelStore()
        store.delete()
        #expect(store.load() == nil)
        store.save(channel)
        #expect(store.load() == channel)
        store.save(ParentChannel(server: channel.server, conversation: "andersxy", secret: channel.secret, mentions: ["ben"]))
        #expect(store.load()?.conversation == "andersxy")
        store.delete()
        #expect(store.load() == nil)
    }

    @Test func removingTheChannelStopsNotificationsImmediately() async {
        let store = InMemoryParentChannelStore(channel)
        let poster = RecordingPoster(status: 201)
        let notifier = TalkBotNotifier(channel: { store.load() }, poster: poster)
        #expect(await notifier.notifyNewWish(openCount: 1) == .sent)
        store.delete()
        #expect(await notifier.notifyNewWish(openCount: 1) == .notConfigured)
        #expect(poster.requests.count == 1)
    }

    @Test func onlyNewWishesNotifyParents() async throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Kind")
        let repo = WishRepository(context: context)
        let poster = RecordingPoster(status: 201)
        let notifier = TalkBotNotifier(channel: { channel }, poster: poster)

        let first = try repo.submit(.thema("Dinos"), for: profile)
        #expect(repo.openWishCount() == 1)
        let sent = WishRepository.notifyParents(after: first, openCount: repo.openWishCount(), notifier: notifier)
        #expect(await sent?.value == .sent)

        let duplicate = try repo.submit(.thema("dinos"), for: profile)
        #expect(WishRepository.notifyParents(after: duplicate, openCount: repo.openWishCount(), notifier: notifier) == nil)

        _ = try repo.submit(.thema("Vulkane"), for: profile)
        #expect(repo.openWishCount() == 2)
        #expect(poster.requests.count == 1)
        let body = try JSONSerialization.jsonObject(with: try #require(poster.requests.first).body) as? [String: String]
        #expect(body?["message"] == "@anna SideTube: neuer Wunsch (1 offen)")
    }

    @Test func failingNotifierLeavesTheWishAlone() async throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Kind")
        let repo = WishRepository(context: context)
        let notifier = TalkBotNotifier(channel: { channel }, poster: RecordingPoster(error: URLError(.timedOut)))
        let result = try repo.submit(.thema("Pferde"), for: profile)
        #expect(await WishRepository.notifyParents(after: result, openCount: 1, notifier: notifier)?.value == .failed(status: nil))
        #expect(repo.pending(of: profile.id).count == 1)
    }
}

/// Einstellungsseite „Eltern benachrichtigen" (Phase 4), ohne Oberfläche.
struct ParentChannelSetupTests {
    private let secret = String(repeating: "0123456789abcdef", count: 4)
    private var code: String {
        #"{"v":1,"art":"talk","server":"https://wolke.example.org","gespraech":"abcd2345","schluessel":"\#(secret)","erwaehnen":["anna"]}"#
    }

    @Test func applyTestRemove() async {
        let store = InMemoryParentChannelStore()
        let poster = RecordingPoster(status: 201)
        let setup = ParentChannelSetup(store: store, notifier: TalkBotNotifier(channel: { store.load() }, poster: poster))
        #expect(setup.channel == nil)

        setup.apply(code: "Unsinn")
        #expect(setup.channel == nil)
        #expect(setup.message?.hasPrefix("Der Einrichtungscode passt nicht") == true)

        setup.apply(code: code)
        #expect(store.load()?.conversation == "abcd2345")

        await setup.sendTest()
        #expect(setup.message == "Gesendet. Die Meldung erscheint in der Nextcloud-App.")
        let body = try? JSONSerialization.jsonObject(with: poster.requests.first?.body ?? Data()) as? [String: String]
        #expect(body?["message"] == "@anna SideTube: Test der Benachrichtigung")

        // Ein ungültiger zweiter Code lässt die Einrichtung stehen.
        setup.apply(code: "{}")
        #expect(store.load() != nil)

        setup.remove()
        #expect(store.load() == nil)
        #expect(setup.channel == nil)
    }

    @Test func rejectedTestIsExplained() async {
        let store = InMemoryParentChannelStore()
        let setup = ParentChannelSetup(store: store, notifier: TalkBotNotifier(channel: { store.load() }, poster: RecordingPoster(status: 401)))
        setup.apply(code: code)
        await setup.sendTest()
        #expect(setup.message == "Abgelehnt: Schlüssel oder Bot passen nicht (HTTP 401).")
    }
}
