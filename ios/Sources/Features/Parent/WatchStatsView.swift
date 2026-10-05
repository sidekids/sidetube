// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// Nutzungsstatistik je Profil (FR-11): Zeitraum wählen, Gesamtzeit, Tagesverlauf, meistgesehene Videos.
struct WatchStatsView: View {
    @Environment(\.modelContext) private var context
    let profile: KidProfile
    @State private var period: StatsPeriod = .week

    private var stats: WatchStats {
        WatchStatsCalculator.stats(for: profile.watchHistory, period: period)
    }

    var body: some View {
        let stats = stats
        List {
            Section {
                Picker("Zeitraum", selection: $period) {
                    ForEach(StatsPeriod.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("stats.period")
            }

            Section {
                LabeledContent("Sehzeit", value: stats.totalFormatted)
                LabeledContent("Videos", value: "\(stats.videoCount)")
                if let limit = profile.dailyLimitMinutes {
                    LabeledContent("Tageslimit", value: "\(limit) min")
                }
            } footer: {
                Text("Gezählt wird die tatsächlich gespielte Zeit, nicht die Länge der Videos.")
            }

            if stats.days.count > 1 {
                Section("Pro Tag") {
                    DayBarChart(days: stats.days)
                        .frame(height: 140)
                        .padding(.vertical, 4)
                }
            }

            Section("Am meisten gesehen") {
                if stats.topVideos.isEmpty {
                    ContentUnavailableView("Nichts geschaut", systemImage: "chart.bar",
                                           description: Text("In diesem Zeitraum wurde kein Video abgespielt."))
                } else {
                    ForEach(stats.topVideos) { video in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(video.title).lineLimit(2)
                            Text("\(WatchStats.format(seconds: video.seconds)) · \(video.plays)×")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .navigationTitle("Nutzung")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Balken je Tag – bewusst ohne Charts-Framework, damit die Zielversion iOS 17 bleibt.
private struct DayBarChart: View {
    let days: [WatchStats.Day]

    private var maximum: Int { max(days.map(\.seconds).max() ?? 0, 1) }

    var body: some View {
        GeometryReader { proxy in
            let spacing: CGFloat = days.count > 14 ? 2 : 4
            let labelHeight: CGFloat = days.count <= 7 ? 20 : 0
            HStack(alignment: .bottom, spacing: spacing) {
                ForEach(days) { day in
                    VStack(spacing: 4) {
                        RoundedRectangle(cornerRadius: 3, style: .continuous)
                            .fill(day.seconds > 0 ? Color.accentColor : Color.secondary.opacity(0.15))
                            .frame(height: max(2, (proxy.size.height - labelHeight) * CGFloat(day.seconds) / CGFloat(maximum)))
                        if days.count <= 7 {
                            Text(day.date, format: .dateTime.weekday(.narrow))
                                .font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(day.date.formatted(date: .abbreviated, time: .omitted))
                    .accessibilityValue(WatchStats.format(seconds: day.seconds))
                }
            }
            // Die Balken wachsen von unten; ohne das sitzt die Reihe am oberen Rand.
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .bottom)
        }
    }
}
