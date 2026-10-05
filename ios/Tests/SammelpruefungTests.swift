// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

/// ADR 0004: mehrere Einträge auf einmal prüfen – Regel und Speichern.
struct SammelpruefungTests {
    typealias P = Sammelpruefung.Pruefling

    // MARK: Sammelbar ja/nein (gleich Android `SammelpruefungTest`)

    @Test func unauffaelligerOffenerEintragIstSammelbar() {
        #expect(Sammelpruefung.grund(P(ageMin: 6, category: .knowledge, quellenStufe: .perVideoReview)) == nil)
        #expect(Sammelpruefung.istSammelbar(P()))
        #expect(Sammelpruefung.istSammelbar(P(quellenStufe: .trustedSeries)))
    }

    @Test func filtertrefferBrauchenEinzelpruefung() {
        #expect(Sammelpruefung.grund(P(risikoThemen: [.war])) == .filtertreffer)
        #expect(Sammelpruefung.grund(P(vermerkteTreffer: true)) == .filtertreffer)
    }

    @Test func frischeVorpruefungMitHartemOderThemenTrefferZaehltAuch() {
        #expect(Sammelpruefung.grund(P(vorpruefung: RiskAssessment(hardBlockTerms: ["nsfw"]))) == .filtertreffer)
        #expect(Sammelpruefung.grund(P(vorpruefung: RiskAssessment(topics: [.fear]))) == .filtertreffer)
        // Shorts und Live sind kein Treffer.
        #expect(Sammelpruefung.grund(P(vorpruefung: RiskAssessment(isShort: true, isLive: true))) == nil)
    }

    @Test func nachrichtenMitElternpruefungOderBelastendBleibenDraussen() {
        #expect(Sammelpruefung.grund(P(isNews: true, newsStatus: .parentReview)) == .nachricht)
        #expect(Sammelpruefung.grund(P(isNews: true, newsStatus: .sensitive)) == .nachricht)
        #expect(Sammelpruefung.grund(P(isNews: true, newsStatus: .safe)) == nil)
    }

    @Test func gesperrteUndNurElternQuellenBleibenDraussen() {
        #expect(Sammelpruefung.grund(P(quellenStufe: .blocked)) == .quelleGesperrt)
        #expect(Sammelpruefung.grund(P(quellenStufe: .parentOnly)) == .quelleNurFuerEltern)
        #expect(Sammelpruefung.grund(P(istKanal: true, quellenStufe: .blocked)) == .quelleGesperrt)
    }

    @Test func wasNichtMehrOffenIstWirdNichtAngefasst() {
        #expect(Sammelpruefung.grund(P(istOffen: false)) == .nichtOffen)
        #expect(Sammelpruefung.grund(P(istOffen: false, quellenStufe: .blocked)) == .nichtOffen)
    }

    @Test func dieQuelleGehtDemFiltertrefferAlsGrundVor() {
        #expect(Sammelpruefung.grund(P(quellenStufe: .blocked, risikoThemen: [.war])) == .quelleGesperrt)
    }

    @Test func grundtexteWieAndroid() {
        #expect(Sammelpruefung.Grund.allCases.map(\.text)
                == ["nicht mehr offen", "Quelle gesperrt", "nur für Eltern", "Filtertreffer", "Nachricht prüfen"])
    }

    @Test func hinweisNenntAnzahlUndGruende() {
        #expect(Sammelpruefung.hinweis([]) == nil)
        #expect(Sammelpruefung.hinweis([.filtertreffer]) == "1 Eintrag braucht eine Einzelprüfung: Filtertreffer (1)")
        #expect(Sammelpruefung.hinweis([.filtertreffer, .quelleGesperrt, .filtertreffer])
                == "3 brauchen eine Einzelprüfung: Quelle gesperrt (1), Filtertreffer (2)")
    }

    // MARK: Freigabe je Eintrag

    @Test func wieVorgeschlagenBehaeltJederEintragSeineVorgaben() {
        let werte = Sammelpruefung.Werte()
        #expect(Sammelpruefung.freigabe(P(ageMin: 5, ageMax: 10, category: .knowledge), mit: werte)
                == .init(ageMin: 5, ageMax: 10, category: .knowledge, trust: nil))
        // Ein Alter von 0 bleibt 0 – wie ein unverändertes Freigeben im Einzelweg.
        #expect(Sammelpruefung.freigabe(P(ageMin: 0, category: nil), mit: werte)
                == .init(ageMin: 0, category: nil, trust: nil))
    }

    @Test func gesetztesAlterUndGesetzteKategorieGeltenFuerAlle() {
        let werte = Sammelpruefung.Werte(alter: 7, kategorie: .kategorie(.music))
        #expect(Sammelpruefung.freigabe(P(ageMin: 5, category: .knowledge), mit: werte)
                == .init(ageMin: 7, category: .music, trust: nil))
    }

    @Test func keineKategorieIstEineBewussteWahl() {
        #expect(Sammelpruefung.freigabe(P(category: .knowledge), mit: .init(kategorie: .keine)).category == nil)
    }

    @Test func kategorieMindestalterHebtJeEintragAn() {
        let werte = Sammelpruefung.Werte(kategorie: .kategorie(.animeManga))
        let jung = Sammelpruefung.freigabe(P(ageMin: 6), mit: werte)
        #expect(jung.ageMin == 12)
        #expect(jung.kategorieHebtAn)
        let alt = Sammelpruefung.freigabe(P(ageMin: 14), mit: werte)
        #expect(alt.ageMin == 14)
        #expect(!alt.kategorieHebtAn)
    }

    @Test func vorgeschlageneKategorieHebtGesetztesAlterEbenfallsAn() {
        #expect(Sammelpruefung.freigabe(P(category: .mangaDrawing), mit: .init(alter: 4)).ageMin == 8)
    }

    @Test func hoechstalterFaelltNieUnterDasAngehobeneMindestalter() {
        #expect(Sammelpruefung.freigabe(P(ageMax: 8), mit: .init(alter: 10)).ageMax == 10)
    }

    @Test func gesetztesAlterBleibtImWaehlbarenBereich() {
        #expect(Sammelpruefung.freigabe(P(), mit: .init(alter: 1)).ageMin == 3)
        #expect(Sammelpruefung.freigabe(P(), mit: .init(alter: 30)).ageMin == 16)
    }

    @Test func kanaeleBekommenGewaehlteStufeUndIhreEigeneVorauswahl() {
        // Wie Android: Alter 0 am Kandidaten fällt auf die Quelle zurück, die Kategorie des Kandidaten gilt.
        let kanal = P(istKanal: true, ageMin: 0, category: .knowledge, quellenStufe: .perVideoReview,
                      quellenAlter: 8, quellenKategorie: .music)
        #expect(Sammelpruefung.freigabe(kanal, mit: .init(kanalStufe: .trustedSeries))
                == .init(ageMin: 8, category: .knowledge, trust: .trustedSeries))
    }

    @Test func kanalOhneVorgabenBekommtAlter6UndGesetzteWerteGeltenAuchFuerIhn() {
        #expect(Sammelpruefung.freigabe(P(istKanal: true), mit: .init()) == .init(ageMin: 6, category: nil, trust: .perVideoReview))
        #expect(Sammelpruefung.freigabe(P(istKanal: true), mit: .init(alter: 9, kategorie: .kategorie(.animeManga))).ageMin == 12)
    }

    @Test func gesperrtIstInDerSammelwahlNichtWaehlbar() {
        #expect(!Sammelpruefung.waehlbareKanalStufen.contains(.blocked))
        #expect(Sammelpruefung.waehlbareKanalStufen.contains(.parentOnly))
        #expect(Sammelpruefung.Werte().kanalStufe == .perVideoReview)
        // Ein untergeschobenes „Gesperrt" fällt auf die vorsichtige Standardstufe zurück.
        #expect(Sammelpruefung.freigabe(P(istKanal: true), mit: .init(kanalStufe: .blocked)).trust == .perVideoReview)
        #expect(Sammelpruefung.freigabe(P(), mit: .init(kanalStufe: .trustedChildSource)).trust == nil)
    }

    // MARK: Speichern

    private func setUp() throws -> (ModelContext, KidProfile, CurationRepository) {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mia")
        let curation = CurationRepository(context: context, now: { Date(timeIntervalSince1970: 1_790_000_000) })
        return (context, profile, curation)
    }

    private func video(_ id: String, _ title: String, kanal: String? = nil) -> WhitelistItemDraft {
        WhitelistItemDraft(type: .video, youtubeId: id, title: title, thumbnailUrl: "https://t/\(id)", channelTitle: "K",
                           sourceChannelId: kanal)
    }

    private func kanalEntwurf(_ id: String, _ title: String) -> WhitelistItemDraft {
        WhitelistItemDraft(type: .channel, youtubeId: id, title: title, thumbnailUrl: "", channelTitle: nil, sourceChannelId: id)
    }

    @Test func sammelfreigabeGibtNurSammelbaresFreiJederMitEigenemVerlauf() throws {
        let (context, profile, curation) = try setUp()
        try curation.ensureSources([SourceDefinition(channelId: "UCgesperrt", title: "Gesperrt", trust: .blocked),
                                    SourceDefinition(channelId: "UCeltern", title: "Eltern", trust: .parentOnly)])
        let a = try curation.discover(video("vidA0000001", "Wie Bienen fliegen"), for: profile)
        let b = try curation.discover(video("vidB0000001", "Vulkane erklärt"), for: profile)
        let risiko = try curation.discover(video("vidR0000001", "Krieg erklärt"), for: profile)
        let eltern = try curation.discover(video("vidE0000001", "Nur für uns", kanal: "UCeltern"), for: profile)
        // Gesperrt wird erst nach der Entdeckung (sonst lehnt discover ab).
        let spaet = try curation.discover(video("vidS0000001", "Später gesperrt", kanal: "UCspaet"), for: profile)
        try curation.ensureSources([SourceDefinition(channelId: "UCspaet", title: "Spät", trust: .blocked)])

        let ergebnis = try curation.sammelFreigeben([a, b, risiko, eltern, spaet],
                                                   mit: .init(alter: 7, kategorie: .kategorie(.nature)), actor: "Eltern")
        #expect(ergebnis.erledigt == ["vidA0000001", "vidB0000001"])
        #expect(ergebnis.uebersprungen.map(\.grund) == [.filtertreffer, .quelleNurFuerEltern, .quelleGesperrt])

        for item in [a, b] {
            #expect(item.approvalStatus == .approved)
            #expect(item.ageMin == 7)
            #expect(item.category == .nature)
            #expect(item.approvedBy == "Eltern")
            #expect(item.parentNotes == nil, "Der Vermerk gehört in den Verlauf, nicht in die Anmerkung für die Familie")
            let freigabe = curation.events(for: item.youtubeId).filter { $0.decision == .approved }
            #expect(freigabe.count == 1)
            #expect(freigabe.first?.actor == "Eltern")
            #expect(freigabe.first?.note == "Sammelprüfung")
        }
        for item in [risiko, eltern, spaet] {
            #expect(item.approvalStatus == .reviewRequired)
            #expect(curation.events(for: item.youtubeId).allSatisfy { $0.decision != .approved })
        }
        #expect(curation.pendingReview(in: profile).count == 3)
        _ = context
    }

    @Test func wieVorgeschlagenUebernimmtAlterUndKategorieDerQuelle() throws {
        let (_, profile, curation) = try setUp()
        try curation.ensureSources([SourceDefinition(channelId: "UCreihe", title: "Reihe", trust: .trustedSeries,
                                                     defaultAgeMin: 8, defaultCategory: .technology)])
        let item = try curation.discover(video("vidQ0000001", "Roboter bauen", kanal: "UCreihe"), for: profile)
        item.parentNotes = "Mit Papa schauen"
        item.ageMax = 12
        try curation.sammelFreigeben([item], mit: .init(), actor: "Eltern")
        #expect(item.approvalStatus == .approved)
        #expect(item.ageMin == 8)
        #expect(item.category == .technology)
        #expect(item.ageMax == 12)
        #expect(item.parentNotes == "Mit Papa schauen")
        #expect(curation.events(for: item.youtubeId).first { $0.decision == .approved }?.note == "Sammelprüfung · Mit Papa schauen")
    }

    @Test func belastendeNachrichtBleibtStehen() throws {
        let (_, profile, curation) = try setUp()
        try curation.ensureSources([SourceDefinition(channelId: "UCnews", title: "News", trust: .trustedSeries, isNews: true)])
        let nachricht = try curation.discover(video("vidN0000001", "Seepferdchen im Meer", kanal: "UCnews"), for: profile)
        #expect(nachricht.isNews)
        nachricht.newsStatus = .sensitive
        let ergebnis = try curation.sammelFreigeben([nachricht], mit: .init(), actor: "Eltern")
        #expect(ergebnis.erledigt.isEmpty)
        #expect(ergebnis.uebersprungen.map(\.grund) == [.nachricht])
        #expect(nachricht.approvalStatus == .reviewRequired)
    }

    @Test func kanalBekommtGewaehlteStufeInDerQuelle() throws {
        let (_, profile, curation) = try setUp()
        let kanal = try curation.discover(kanalEntwurf("UCkandidat000", "Bastelkanal"), for: profile,
                                          sourceChannelId: "UCkandidat000", actor: "Eltern")

        let ergebnis = try curation.sammelFreigeben([kanal], mit: .init(alter: 7, kategorie: .kategorie(.animeManga),
                                                                         kanalStufe: .trustedChildSource), actor: "Eltern")
        #expect(ergebnis.erledigt == ["UCkandidat000"])
        #expect(kanal.approvalStatus == .approved)
        #expect(kanal.ageMin == 12)
        let quelle = try #require(curation.source(channelId: "UCkandidat000"))
        #expect(quelle.trust == .trustedChildSource)
        #expect(quelle.defaultAgeMin == 12)
        #expect(quelle.defaultCategory == .animeManga)
        #expect(curation.events(for: "source:UCkandidat000").count == 1)

        // „Gesperrt" lässt sich gesammelt nicht wählen – auch nicht durch einen untergeschobenen Wert.
        let zweiter = try curation.discover(kanalEntwurf("UCkandidat001", "Noch einer"), for: profile,
                                            sourceChannelId: "UCkandidat001", actor: "Eltern")
        try curation.sammelFreigeben([zweiter], mit: .init(kanalStufe: .blocked), actor: "Eltern")
        #expect(curation.source(channelId: "UCkandidat001")?.trust == .perVideoReview)
        #expect(zweiter.approvalStatus == .approved)
    }

    @Test func freigabeErfuelltPassendeWuensche() throws {
        let (context, profile, curation) = try setUp()
        let wuensche = WishRepository(context: context)
        let folge = try wuensche.submit(WishDraft(kind: .neueFolge, videoId: "vidW0000001", videoTitle: "Neue Folge",
                                                  channelId: "UCreihe2", channelTitle: "Reihe"), for: profile).wish
        let mehr = try wuensche.submit(WishDraft(kind: .mehrDavon, videoId: "vidW0000001", videoTitle: "Neue Folge",
                                                 channelId: "UCreihe2", channelTitle: "Reihe"), for: profile).wish
        let item = try curation.discover(video("vidW0000001", "Neue Folge", kanal: "UCreihe2"), for: profile)
        let ergebnis = try curation.sammelFreigeben([item], mit: .init(), actor: "Eltern")
        #expect(ergebnis.erfuellteWuensche == 1)
        #expect(folge.status == .erfuellt)
        #expect(folge.fulfilledYoutubeId == "vidW0000001")
        #expect(mehr.status == .offen, "„Mehr davon“ meint das Ausgangsvideo, nicht diese Freigabe")
    }

    /// Auch die **Einzel**freigabe erfüllt passende Wünsche – wie die Sammelfreigabe und wie Android.
    @Test func einzelfreigabeErfuelltPassendeWuensche() throws {
        let (context, profile, curation) = try setUp()
        let folge = try WishRepository(context: context).submit(
            WishDraft(kind: .neueFolge, videoId: "vidE0000001", videoTitle: "Neue Folge",
                      channelId: "UCreihe3", channelTitle: "Reihe"), for: profile).wish
        let item = try curation.discover(video("vidE0000001", "Neue Folge", kanal: "UCreihe3"), for: profile)
        let erfuellt = try curation.approveUndErfuelleWuensche(
            item, with: .init(ageMin: 6, ageMax: nil, category: nil, newsStatus: nil, parentNotes: nil), actor: "Eltern")
        #expect(erfuellt == 1)
        #expect(item.approvalStatus == .approved)
        #expect(folge.status == .erfuellt)
        #expect(folge.fulfilledYoutubeId == "vidE0000001")
    }

    @Test func sammelablehnungLehntJedenEinzelnAbUndLaesstNichtSammelbaresStehen() throws {
        let (_, profile, curation) = try setUp()
        let a = try curation.discover(video("vidX0000001", "Eins"), for: profile)
        let b = try curation.discover(video("vidX0000002", "Zwei"), for: profile)
        let risiko = try curation.discover(video("vidX0000003", "Krieg drei"), for: profile)
        let ergebnis = try curation.sammelAblehnen([a, b, risiko], actor: "Eltern")
        #expect(ergebnis.erledigt == ["vidX0000001", "vidX0000002"])
        #expect(ergebnis.uebersprungen.map(\.grund) == [.filtertreffer])
        for item in [a, b] {
            #expect(item.approvalStatus == .rejected)
            let ablehnung = curation.events(for: item.youtubeId).filter { $0.decision == .rejected }
            #expect(ablehnung.count == 1)
            #expect(ablehnung.first?.actor == "Eltern")
            #expect(ablehnung.first?.note == "Sammelprüfung")
        }
        #expect(risiko.approvalStatus == .reviewRequired)
        #expect(curation.pendingReview(in: profile).count == 1)
    }

    @Test func zurueckGestellterHarterTrefferWirdFrischErkannt() throws {
        let (_, profile, curation) = try setUp()
        let item = try curation.discover(video("vidH0000001", "Anime nsfw Clip"), for: profile)
        #expect(item.approvalStatus == .rejected)
        // Eltern holen ihn per „Zurück zur Prüfung" wieder in die Liste; Themen stehen am Eintrag nicht.
        try curation.defer_(item, actor: "Eltern")
        item.sensitiveTopics = []
        item.editorialNotes = nil
        let ergebnis = try curation.sammelFreigeben([item], mit: .init(), actor: "Eltern")
        #expect(ergebnis.erledigt.isEmpty)
        #expect(ergebnis.uebersprungen.map(\.grund) == [.filtertreffer])
        #expect(item.approvalStatus == .reviewRequired)
    }

    @Test func wasInzwischenEntschiedenIstBleibtUnberuehrt() throws {
        let (_, profile, curation) = try setUp()
        let item = try curation.discover(video("vidO0000001", "Ruhig"), for: profile)
        try curation.reject(item, actor: "Eltern")
        let ergebnis = try curation.sammelFreigeben([item], mit: .init(), actor: "Eltern")
        #expect(ergebnis.uebersprungen.map(\.grund) == [.nichtOffen])
        #expect(item.approvalStatus == .rejected)
    }
}
