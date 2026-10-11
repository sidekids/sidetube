// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// Elternbereich: Profile mit Kennzahlen, Einstieg in Whitelist, PIN ändern, Über. FR-03.5, FR-11.1
struct ParentDashboardView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \KidProfile.createdAt) private var profiles: [KidProfile]
    @State private var showNewProfile = false
    @State private var showChangePIN = false
    @State private var showAbout = false
    @State private var showPedagogy = false
    @State private var showKioskTip = false
    @State private var errorMessage: String?
    let onLock: () -> Void

    var body: some View {
        NavigationStack {
            Group {
                if profiles.isEmpty {
                    ContentUnavailableView {
                        Label("Noch kein Profil", systemImage: "person.crop.circle.badge.plus")
                    } description: {
                        Text("Lege für jedes Kind ein Profil mit eigener Whitelist an.")
                    } actions: {
                        Button("Profil anlegen") { showNewProfile = true }.buttonStyle(.borderedProminent)
                        Button("Warum SideTube?") { showPedagogy = true }
                    }
                } else {
                    List {
                        Section("Profile") {
                            ForEach(profiles) { profile in
                                NavigationLink(value: profile) { ProfileRow(profile: profile) }
                            }
                            .onDelete(perform: delete)
                        }
                    }
                }
            }
            .navigationTitle("Einstellungen")
            .environment(\.parentalGateRequired, false)
            .navigationDestination(for: KidProfile.self) { WhitelistManagerView(profile: $0) }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Sperren", systemImage: "lock.fill", action: onLock)
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button("Neues Profil", systemImage: "plus") { showNewProfile = true }
                    Menu {
                        Button("PIN ändern", systemImage: "key") { showChangePIN = true }
                        NavigationLink { SourceTrustView() } label: { Label("Quellen & Sicherheitsstufen", systemImage: "shield.lefthalf.filled") }
                        NavigationLink { ParentChannelView() } label: { Label("Eltern benachrichtigen", systemImage: "bell.badge") }
                        Button("Kind in der App halten", systemImage: "lock.iphone") { showKioskTip = true }
                        Button("Warum SideTube? (für Eltern)", systemImage: "book") { showPedagogy = true }
                        Button("Über SideTube", systemImage: "info.circle") { showAbout = true }
                    } label: { Image(systemName: "ellipsis.circle") }
                }
            }
            .sheet(isPresented: $showNewProfile) { ProfileEditorView(profile: nil) }
            .sheet(isPresented: $showChangePIN) { ChangePINView() }
            .sheet(isPresented: $showAbout) { AboutView() }
            .sheet(isPresented: $showPedagogy) { PedagogyView() }
            .alert("Geführter Zugriff", isPresented: $showKioskTip) {
                Button("Verstanden", role: .cancel) {}
            } message: {
                Text("iOS erlaubt Apps keinen eigenen Kiosk-Modus. Nutze den Geführten Zugriff: Einstellungen → Bedienungshilfen → Geführter Zugriff einschalten und einen Code setzen. Dann in SideTube die Seitentaste dreimal drücken → Start. Beenden wieder mit Dreifachklick und Code.")
            }
            .alert("Fehler", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("OK", role: .cancel) {}
            } message: { Text(errorMessage ?? "") }
        }
    }


    private func delete(at offsets: IndexSet) {
        let repo = ProfileRepository(context: context)
        for index in offsets {
            do { try repo.delete(profiles[index]) } catch { errorMessage = error.localizedDescription }
        }
    }
}

private struct ProfileRow: View {
    @Environment(\.modelContext) private var context
    let profile: KidProfile

    var body: some View {
        let watched = WatchTimeRepository(context: context).secondsWatched(by: profile, on: .now) / 60
        VStack(alignment: .leading, spacing: 4) {
            Text(profile.name).font(.headline)
            // Drei kurze Zeilen statt einer: Zaehler, Regeln, Sehzeit. In einer Zeile brachen englische Woerter
            // silbenweise um („ap-prov-ed“, „20:0 0“); jede Angabe bleibt jetzt einzeilig.
            let pending = CurationRepository(context: context).pendingReview(in: profile).count
            let wishes = WishRepository(context: context).pending(of: profile.id).count
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 12) { zaehler(pending: pending, wishes: wishes) }
                HStack(spacing: 12) { regeln() }
                sehzeit(watched: watched)
            }
            .lineLimit(1)
            .font(.caption).foregroundStyle(.secondary)
        }
    }

    @ViewBuilder private func zaehler(pending: Int, wishes: Int) -> some View {
        Label("\(profile.whitelistItems.filter { $0.approvalStatus == .approved }.count) frei", systemImage: "checklist")
        if pending > 0 { Label("\(pending) prüfen", systemImage: "exclamationmark.circle").foregroundStyle(.orange) }
        if wishes > 0 {
            Label(wishes == 1 ? String(localized: "1 Wunsch") : String(localized: "\(wishes) Wünsche"), systemImage: "star.bubble")
                .foregroundStyle(.orange)
        }
    }

    private func sehzeit(watched: Int) -> some View {
        Label(profile.dailyLimitMinutes.map { String(localized: "\(watched)/\($0) min heute") } ?? String(localized: "\(watched) min heute"), systemImage: "clock")
    }

    @ViewBuilder private func regeln() -> some View {
        Text(profile.ageBand.title)
        if profile.bedtimeEnabled {
            Label("ab \(BedtimeEvaluator.format(minutes: profile.bedtimeStartMinutes))", systemImage: "bed.double")
        }
    }
}

