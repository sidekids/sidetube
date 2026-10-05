// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

/// ADR 0003: Kanal beim Hinzufügen einstufen – Regel und Speichern.
struct KanaleinstufungTests {
    // MARK: Reine Regel

    @Test func ohneQuelleGiltDieVorsichtigeVorgabe() {
        let einstufung = Kanaleinstufung.vorauswahl(aus: nil)
        #expect(einstufung == Kanaleinstufung(trust: .perVideoReview, ageMin: 6, category: nil))
        #expect(einstufung.ergebnis == .hinzufuegen)
    }

    @Test func vorauswahlUebernimmtWasDieQuelleTraegt() {
        let source = CuratedSource(channelId: "UCa", title: "A", trust: .trustedSeries, defaultAgeMin: 9, defaultCategory: .nature)
        #expect(Kanaleinstufung.vorauswahl(aus: source) == Kanaleinstufung(trust: .trustedSeries, ageMin: 9, category: .nature))
    }

    @Test func mindestaltersVorauswahlBleibtImWaehlbarenBereich() {
        // 0 heißt im Register „nicht festgelegt" → Standard, nicht 3.
        #expect(Kanaleinstufung.vorauswahl(trust: .trustedChildSource, defaultAgeMin: 0, defaultCategory: nil).ageMin == 6)
        #expect(Kanaleinstufung.vorauswahl(trust: nil, defaultAgeMin: 1, defaultCategory: nil).ageMin == 3)
        #expect(Kanaleinstufung.vorauswahl(trust: nil, defaultAgeMin: 18, defaultCategory: nil).ageMin == 16)
        #expect(Kanaleinstufung.vorauswahl(trust: nil, defaultAgeMin: nil, defaultCategory: nil).trust == .perVideoReview)
    }

    @Test func kategorieMitHoeheremMindestalterHebtDasAlterAn() {
        var einstufung = Kanaleinstufung(trust: .perVideoReview, ageMin: 6, category: .animeManga)
        #expect(einstufung.effektivesMindestalter == 12)
        #expect(einstufung.kategorieHebtAlterAn)
        einstufung.ageMin = 14
        #expect(einstufung.effektivesMindestalter == 14)
        #expect(!einstufung.kategorieHebtAlterAn)
        einstufung.category = .knowledge
        einstufung.ageMin = 6
        #expect(einstufung.effektivesMindestalter == 6)
        #expect(!einstufung.kategorieHebtAlterAn)
    }

    @Test func nurGesperrtNimmtNichtAuf() {
        for stufe in SourceTrust.allCases {
            let erwartet: Kanaleinstufung.Ergebnis = stufe == .blocked ? .sperren : .hinzufuegen
            #expect(Kanaleinstufung(trust: stufe).ergebnis == erwartet)
            #expect(!stufe.erklaerung.isEmpty)
        }
    }

    // MARK: Speichern

    private func setUp() throws -> (ModelContext, KidProfile, CurationRepository) {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mia")   // Altersprofil 9–11
        let curation = CurationRepository(context: context, now: { Date(timeIntervalSince1970: 1_790_000_000) })
        return (context, profile, curation)
    }

    private func kanal(_ id: String = "UCneuerKanal0000000000", provider: ContentProvider = .youtube) -> WhitelistItemDraft {
        WhitelistItemDraft(type: .channel, youtubeId: id, title: "Neuer Kanal", thumbnailUrl: "https://t/c", channelTitle: nil,
                           provider: provider, sourceChannelId: id, sourceUrl: "https://www.youtube.com/channel/\(id)")
    }

    private func quellenEreignisse(_ context: ModelContext, _ channelId: String) -> [ReviewEvent] {
        CurationRepository(context: context).events(for: "source:\(channelId)")
    }

    @Test func neuerKanalLegtQuelleAnUndIstGleichFreigegeben() throws {
        let (context, profile, curation) = try setUp()
        let result = try curation.kanalAufnehmen(kanal(), als: Kanaleinstufung(trust: .trustedChildSource, ageMin: 7, category: .knowledge),
                                                 for: profile, actor: "Eltern")
        #expect(result == .aufgenommen(youtubeId: "UCneuerKanal0000000000"))

        let source = try #require(curation.source(channelId: "UCneuerKanal0000000000"))
        #expect(source.trust == .trustedChildSource)
        #expect(source.defaultAgeMin == 7)
        #expect(source.defaultCategory == .knowledge)

        let item = try #require(profile.whitelistItems.first)
        #expect(item.type == .channel)
        #expect(item.approvalStatus == .approved)
        #expect(item.approvedBy == "Eltern")
        #expect(item.ageMin == 7 && item.category == .knowledge)
        #expect(WhitelistRepository(context: context).visibleItems(of: profile, type: .channel).count == 1)

        let ereignis = try #require(quellenEreignisse(context, "UCneuerKanal0000000000").first)
        #expect(ereignis.actor == "Eltern")
        #expect(ereignis.decision == .approved)
        #expect(ereignis.note?.contains("Vertrauenswürdige Kinderquelle") == true)
    }

    @Test func bestehendeQuelleWirdAktualisiertNichtVerdoppelt() throws {
        let (context, profile, curation) = try setUp()
        try curation.ensureSources([SourceDefinition(channelId: "UCbekannt", title: "Bekannt", trust: .perVideoReview, defaultAgeMin: 4)])
        let draft = kanal("UCbekannt")
        _ = try curation.kanalAufnehmen(draft, als: Kanaleinstufung(trust: .trustedSeries, ageMin: 6, category: .animeManga),
                                        for: profile, actor: "Eltern")
        let quellen = curation.allSources().filter { $0.channelId == "UCbekannt" }
        #expect(quellen.count == 1)
        #expect(quellen.first?.trust == .trustedSeries)
        #expect(quellen.first?.title == "Bekannt")   // Name aus dem Register bleibt
        // Kategorie-Mindestalter gilt – in Quelle und Eintrag.
        #expect(quellen.first?.defaultAgeMin == 12)
        #expect(profile.whitelistItems.first?.ageMin == 12)
        // Für das 9-jährige Profil also (noch) nicht sichtbar.
        #expect(WhitelistRepository(context: context).visibleItems(of: profile, type: .channel).isEmpty)
    }

    @Test func gesperrtSpeichertQuelleAberNimmtNichtAuf() throws {
        let (context, profile, curation) = try setUp()
        let result = try curation.kanalAufnehmen(kanal(), als: Kanaleinstufung(trust: .blocked), for: profile, actor: "Eltern")
        #expect(result == .gesperrt)
        #expect(profile.whitelistItems.isEmpty)
        #expect(curation.source(channelId: "UCneuerKanal0000000000")?.trust == .blocked)
        #expect(quellenEreignisse(context, "UCneuerKanal0000000000").first?.decision == .blockedSource)
        // Künftige Videos dieses Kanals werden abgewiesen.
        let video = WhitelistItemDraft(type: .video, youtubeId: "vid00000001", title: "Video", thumbnailUrl: "",
                                       sourceChannelId: "UCneuerKanal0000000000")
        #expect(throws: CurationRepository.DiscoverError.blockedSource) { try curation.discover(video, for: profile) }
    }

    @Test func vorhandenerKandidatWirdFreigegebenStattDoppelt() throws {
        let (_, profile, curation) = try setUp()
        let draft = kanal()
        let kandidat = try curation.discover(draft, for: profile, sourceChannelId: draft.youtubeId, actor: "Eltern")
        #expect(kandidat.approvalStatus == .reviewRequired)
        _ = try curation.kanalAufnehmen(draft, als: Kanaleinstufung(trust: .perVideoReview, ageMin: 8), for: profile, actor: "Eltern")
        #expect(profile.whitelistItems.count == 1)
        #expect(kandidat.approvalStatus == .approved && kandidat.ageMin == 8)
    }

    @Test func peerTubeKanalSperrtNieDieGanzeInstanz() throws {
        let (_, profile, curation) = try setUp()
        let instanz = PeerTubeIDs.instanceId(host: "tube.example.org")
        try curation.ensureSources([SourceDefinition(channelId: instanz, title: "Instanz", provider: .peertube,
                                                     trust: .perVideoReview, defaultAgeMin: 6)])
        let kanalId = PeerTubeIDs.channelId(host: "tube.example.org", name: "kanal")
        let draft = kanal(kanalId, provider: .peertube)
        // Vorauswahl fällt auf die Instanz zurück …
        #expect(Kanaleinstufung.vorauswahl(aus: curation.effectiveSource(channelId: kanalId)).trust == .perVideoReview)
        _ = try curation.kanalAufnehmen(draft, als: Kanaleinstufung(trust: .blocked), for: profile, actor: "Eltern")
        // … gespeichert wird aber nur der Kanal.
        #expect(curation.source(channelId: instanz)?.trust == .perVideoReview)
        #expect(curation.source(channelId: kanalId)?.trust == .blocked)
    }
}
