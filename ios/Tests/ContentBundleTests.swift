// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Testing
@testable import sidetube

/// Die gemeinsamen Daten unter `content/` sind für beide Plattformen verbindlich – hier wird geprüft,
/// dass die Swift-Typen und die Dateien nicht auseinanderlaufen.
struct ContentBundleTests {
    private struct Taxonomy: Decodable {
        struct Band: Decodable { var id: String; var title: String; var age: Int }
        struct Category: Decodable { var id: String; var title: String; var minimumAge: Int }
        struct Trust: Decodable { var id: String; var title: String; var allowsChannelBrowsing: Bool }
        var ageBands: [Band]
        var categories: [Category]
        var sourceTrust: [Trust]
        var approvalStatus: [String]
        var newsStatus: [String]
        var sensitiveTopics: [String]
        var providers: [String]
    }

    @Test func taxonomyMatchesTheSwiftTypes() throws {
        let tax = try ContentBundle.load(Taxonomy.self, "taxonomy", in: .schema)
        #expect(tax.ageBands.map(\.id) == AgeBand.allCases.map(\.rawValue))
        #expect(tax.ageBands.allSatisfy { AgeBand(rawValue: $0.id)?.age == $0.age })
        #expect(tax.categories.map(\.id) == ContentCategory.allCases.map(\.rawValue))
        #expect(tax.categories.allSatisfy { ContentCategory(rawValue: $0.id)?.minimumAge == $0.minimumAge })
        #expect(tax.categories.allSatisfy { ContentCategory(rawValue: $0.id)?.title == $0.title })
        #expect(tax.sourceTrust.map(\.id) == SourceTrust.allCases.map(\.rawValue))
        #expect(tax.sourceTrust.allSatisfy { SourceTrust(rawValue: $0.id)?.allowsChannelBrowsing == $0.allowsChannelBrowsing })
        #expect(tax.approvalStatus == ApprovalStatus.allCases.map(\.rawValue))
        #expect(tax.newsStatus == NewsStatus.allCases.map(\.rawValue))
        #expect(tax.sensitiveTopics == SensitiveTopic.allCases.map(\.rawValue))
        #expect(tax.providers == ContentProvider.allCases.map(\.rawValue))
    }

    @Test func sourceRegistryLoadsCompletely() {
        let sources = SourceRegistry.allDefinitions
        #expect(sources.count >= 20, "Register wird aus content/sources.json geladen")
        #expect(Set(sources.map(\.channelId)).count == sources.count, "keine doppelten Kennungen")
        #expect(sources.allSatisfy { !$0.title.isEmpty })
        #expect(SourceRegistry.blockedChannelIds.count == 2)
        #expect(SourceRegistry.peerTubeInstances.allSatisfy { $0.channelId.hasPrefix("pt:") && $0.provider == .peertube })
        #expect(SourceRegistry.definitions.allSatisfy { $0.provider != .peertube })
        // Stufen mit Konsequenz: nur vollständig kindorientierte Quellen dürfen durchstöbert werden
        let browsable = sources.filter { $0.trust.allowsChannelBrowsing }
        #expect(browsable.map(\.title).sorted() == ["Die Maus", "Sesamstraße"])
    }

    @Test func riskTermsLoadAndStillCatchTheObviousCases() {
        #expect(!RiskScreen.hardBlock.isEmpty)
        #expect(RiskScreen.topicTerms.count >= 10)
        #expect(RiskScreen.assess(title: "NSFW Compilation").isHardBlocked)
        #expect(RiskScreen.assess(title: "Bericht über den Krieg in der Ukraine").topics.contains(.war))
        #expect(RiskScreen.assess(title: "Wie entstehen Ebbe und Flut?").topics.isEmpty)
    }

    @Test func everyLibraryReferencesKnownAndAllowedSources() throws {
        let known = Dictionary(uniqueKeysWithValues: SourceRegistry.allDefinitions.map { ($0.channelId, $0) })
        for catalog in SeedLibraryImporter.catalogs(from: .main) {
            let library = try SeedLibraryImporter.load(catalog)
            let channels = library.channels ?? []
            #expect(!library.videos.isEmpty || !channels.isEmpty, "\(catalog.id) ist nicht leer")
            #expect(Set(library.videos.map(\.id)).count == library.videos.count, "\(catalog.id): keine doppelten Videos")
            for video in library.videos {
                // wie in der App: ein PeerTube-Kanal ohne eigene Quelle fällt auf seine Instanz zurück
                let source = known[video.channelId] ?? PeerTubeIDs.instanceId(of: video.channelId).flatMap { known[$0] }
                #expect(source != nil, "\(catalog.id): unbekannte Quelle \(video.channelId)")
                #expect(source?.trust != .blocked, "\(catalog.id): gesperrte Quelle \(video.channelId)")
                #expect(video.ageMin >= (video.category?.minimumAge ?? 0), "\(catalog.id): \(video.id) unter dem Mindestalter der Kategorie")
            }
            // Kanalpakete übernehmen bestehende Freigaben von einem anderen Gerät. Ein Kanal darf
            // dem Register unbekannt sein – dann kommt er als „zu prüfen" an, und die Eltern
            // entscheiden. Gesperrt darf er nie sein, und doppelt auch nicht.
            #expect(Set(channels.map(\.id)).count == channels.count, "\(catalog.id): keine doppelten Kanäle")
            for channel in channels {
                #expect(known[channel.id]?.trust != .blocked, "\(catalog.id): gesperrte Quelle \(channel.id)")
                #expect(known[channel.id] != nil || channel.note != nil,
                        "\(catalog.id): unbekannter Kanal \(channel.id) braucht eine Notiz, warum er dabei ist")
            }
        }
    }
}
