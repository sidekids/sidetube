import SwiftData
import SwiftUI

/// Profil anlegen/bearbeiten: Name, Tageslimit, Schlaf-Playlist. FR-03.1, FR-03.3
struct ProfileEditorView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    let profile: KidProfile?

    @State private var name = ""
    @State private var limitEnabled = false
    @State private var limitMinutes = 60
    @State private var sleepPlaylist = ""
    @State private var ageBand: AgeBand = .kids
    @State private var allowNews = true
    @State private var allowManga = true
    @State private var allowMangaEntertainment = false
    @State private var allowShorts = false
    @State private var autoplayNext = false
    @State private var bedtimeEnabled = true
    @State private var bedtimeStart = BedtimeSettings.defaultStartMinutes
    @State private var bedtimeEnd = BedtimeSettings.defaultEndMinutes
    @State private var bedtimeWeekendOffset = BedtimeSettings.defaultWeekendOffsetMinutes
    @State private var bedtimeSkipUntil: Date?
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Kind") {
                    TextField("Name", text: $name).textInputAutocapitalization(.words)
                }
                Section {
                    Picker("Altersprofil", selection: $ageBand) {
                        ForEach(AgeBand.allCases) { Text($0.title).tag($0) }
                    }
                } footer: { Text("Inhalte mit höherem Mindestalter bleiben unsichtbar. Manga zeichnen ab 8, Anime & Manga ab 12.") }
                Section("Inhalte") {
                    Toggle("Nachrichten (logo!)", isOn: $allowNews)
                    Toggle("Manga zeichnen", isOn: $allowManga)
                    Toggle("Anime & Manga (ab 12)", isOn: $allowMangaEntertainment).disabled(!allowManga || ageBand != .older)
                    Toggle("Shorts erlauben", isOn: $allowShorts)
                    Toggle("Nächstes Video automatisch", isOn: $autoplayNext)
                }
                Section {
                    Toggle("Ruhezeit", isOn: $bedtimeEnabled)
                    if bedtimeEnabled {
                        Picker("Beginn", selection: $bedtimeStart) {
                            ForEach(Array(stride(from: 17 * 60, through: 23 * 60, by: 15)), id: \.self) {
                                Text(BedtimeEvaluator.format(minutes: $0)).tag($0)
                            }
                        }
                        Picker("Ende", selection: $bedtimeEnd) {
                            ForEach(Array(stride(from: 5 * 60, through: 9 * 60, by: 15)), id: \.self) {
                                Text(BedtimeEvaluator.format(minutes: $0)).tag($0)
                            }
                        }
                        Stepper("Fr/Sa \(bedtimeWeekendOffset) min später", value: $bedtimeWeekendOffset, in: 0...120, step: 15)
                        HStack {
                            Text("Vorschlag").foregroundStyle(.secondary)
                            Spacer()
                            ForEach(BedtimeSettings.ageSuggestions, id: \.label) { suggestion in
                                Button(suggestion.label) { bedtimeStart = suggestion.startMinutes }
                                    .buttonStyle(.bordered).font(.caption)
                            }
                        }
                        if let skipUntil = bedtimeSkipUntil, skipUntil > .now {
                            HStack {
                                Label("Ausnahme bis \(skipUntil.formatted(date: .omitted, time: .shortened))", systemImage: "bed.double.circle")
                                    .foregroundStyle(.orange)
                                Spacer()
                                Button("Aufheben") { bedtimeSkipUntil = nil }
                            }
                        }
                    }
                } footer: {
                    Text("In der Ruhezeit ist der Kindermodus gesperrt; 15 und 5 Minuten vorher gibt es einen Hinweis. Die Eltern-PIN hebt die Sperre bis zum Morgen auf.")
                }
                Section {
                    Toggle("Tageslimit", isOn: $limitEnabled)
                    if limitEnabled {
                        Stepper("\(limitMinutes) Minuten pro Tag", value: $limitMinutes, in: 5...300, step: 5)
                    }
                } footer: { Text("Ohne Limit darf unbegrenzt geschaut werden. Die Zeit wird um Mitternacht zurückgesetzt.") }
                Section {
                    TextField("Playlist-URL oder -ID (optional)", text: $sleepPlaylist)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                } header: { Text("Schlaf-Playlist") } footer: { Text("Wird im Schlafmodus abgespielt (Phase 6).") }
                if let errorMessage { Text(errorMessage).foregroundStyle(.red) }
            }
            .navigationTitle(profile == nil ? "Neues Profil" : "Profil bearbeiten")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Abbrechen") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Sichern", action: save).disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear(perform: load)
        }
    }

    private func load() {
        guard let profile else { return }
        name = profile.name
        limitEnabled = profile.dailyLimitMinutes != nil
        limitMinutes = profile.dailyLimitMinutes ?? 60
        sleepPlaylist = profile.sleepPlaylistId ?? ""
        ageBand = profile.ageBand
        allowNews = profile.allowNews
        allowManga = profile.allowManga
        allowMangaEntertainment = profile.allowMangaEntertainment
        allowShorts = profile.allowShorts
        autoplayNext = profile.autoplayNext
        bedtimeEnabled = profile.bedtimeEnabled
        bedtimeStart = profile.bedtimeStartMinutes
        bedtimeEnd = profile.bedtimeEndMinutes
        bedtimeWeekendOffset = profile.bedtimeWeekendOffsetMinutes
        bedtimeSkipUntil = profile.bedtimeSkipUntil
    }

    private func applyRules(to profile: KidProfile) {
        profile.ageBand = ageBand
        profile.allowNews = allowNews
        profile.allowManga = allowManga
        profile.allowMangaEntertainment = allowMangaEntertainment && ageBand == .older
        profile.allowShorts = allowShorts
        profile.autoplayNext = autoplayNext
        profile.bedtimeEnabled = bedtimeEnabled
        profile.bedtimeStartMinutes = bedtimeStart
        profile.bedtimeEndMinutes = bedtimeEnd
        profile.bedtimeWeekendOffsetMinutes = bedtimeWeekendOffset
        profile.bedtimeSkipUntil = bedtimeSkipUntil
    }

    private func save() {
        let playlistId = Self.playlistId(from: sleepPlaylist)
        do {
            let repo = ProfileRepository(context: context)
            if let profile {
                profile.name = name.trimmingCharacters(in: .whitespaces)
                profile.dailyLimitMinutes = limitEnabled ? limitMinutes : nil
                profile.sleepPlaylistId = playlistId
                applyRules(to: profile)
                try repo.save()
            } else {
                let created = try repo.create(name: name, dailyLimitMinutes: limitEnabled ? limitMinutes : nil)
                created.sleepPlaylistId = playlistId
                applyRules(to: created)
                try repo.save()
            }
            dismiss()
        } catch {
            errorMessage = "Konnte nicht sichern: \(error.localizedDescription)"
        }
    }

   /// Akzeptiert eine Playlist-URL oder eine nackte ID; leer → nil.
    static func playlistId(from input: String) -> String? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if case .playlist(let id)? = YouTubeURLParser.parse(trimmed) { return id }
        return trimmed
    }
}
