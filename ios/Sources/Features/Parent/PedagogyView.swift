import SwiftUI

/// Medienpädagogische Erläuterung für Eltern: Was SideTube tut, warum – und was es bewusst nicht tut.
struct PedagogyView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    HStack(spacing: 12) {
                        Image("SideTubeMark").resizable().scaledToFit().frame(width: 44, height: 44).accessibilityHidden(true)
                        SideWordmark(product: .tube, font: .title3)
                    }
                    block("Worum es geht",
                          "Kinder wollen Videos schauen – und vieles davon ist gut. Das Problem ist nicht das Video, sondern die Maschine darum herum: Empfehlungen, Autoplay, Trends, Kommentare, endlose Feeds. SideTube nimmt den Inhalt und lässt die Maschine weg. Ihr wählt aus, euer Kind stöbert frei – aber nur in dieser Auswahl.")
                    block("Kuratieren statt filtern",
                          "Statt Verbotslisten gibt es hier eine Positivliste: Nichts ist sichtbar, bevor ein Erwachsener es geprüft und freigegeben hat. Das ist mehr Arbeit als ein Jugendschutzfilter – aber es entspricht dem, wie Kinder Vertrauen erleben: Jemand hat sich das angesehen und gesagt: „Das ist gut für dich.“ Der automatische Risikofilter hilft beim Sortieren, entscheidet aber nie allein.")
                    block("Altersprofile",
                          "Jedes Profil hat ein Altersband (3–5, 6–8, 9–11, ab 12). Inhalte tragen ein Mindestalter und Kategorien. So kann ein Video für die Neunjährige freigegeben sein, während der Fünfjährige es nicht sieht – ohne dass ihr zwei Bibliotheken pflegen müsst. Quellen wie öffentlich-rechtliche Kinderangebote dürfen durchstöbert werden; gemischte Kanäle nur Video für Video.")
                    block("Nachrichten",
                          "Kindernachrichten sind wertvoll, aber nicht automatisch unbelastend. Beiträge über Krieg, Katastrophen oder Tod werden gekennzeichnet und erscheinen erst, wenn ihr sie bewusst freigebt – am besten, um sie gemeinsam anzuschauen und darüber zu sprechen.")
                    block("Manga als Gestalten, nicht nur Konsum",
                          "Manga ist bei vielen Kindern ein großes Thema. SideTube trennt bewusst: „Manga zeichnen“ (ab 8) sind Tutorials zum Selbermachen – Gesichter, Figuren, Perspektive, eigene Geschichten. „Anime & Manga“ (ab 12) ist Unterhaltung und Szenekultur, die ihr im Profil eigens einschalten müsst. Große Anime-Kanäle sind nicht automatisch kindgerecht; Popularität ist kein Qualitätsmerkmal.")
                    block("Zeit und Rhythmus",
                          "Autoplay ist aus: Nach einem Video entscheidet euer Kind selbst, ob es weitergeht. Tageslimit und Schlaf-Timer setzen einen Rahmen, den die App ruhig durchsetzt – ohne Tricks, mit klaren Bildschirmen und der Eltern-PIN als Ausweg. Die Fernbedienung mit Rad ist bewusst langsam und haptisch: Auswählen wird zur Handlung, nicht zum Wischen.")
                    block("Was SideTube nicht kann",
                          "Der eingebettete YouTube-Player kann am Ende oder in der Pause Vorschläge aus demselben Kanal zeigen; das lässt sich technisch nicht abschalten. Deshalb gilt: Für das freie Stöbern nur vollständig kindorientierte Quellen, alles andere Video für Video. Und: Kein Filter ersetzt das Gespräch. Schaut hin und wieder gemeinsam.")
                    block("Tipps für den Alltag",
                          "• Startet klein – zehn gute Videos sind mehr wert als hundert wahrscheinlich okaye.\n• Lasst euer Kind Wünsche äußern (Empfehlungen landen zur Prüfung bei euch).\n• Prüft alle paar Monate: Kanäle ändern sich, die App erinnert daran.\n• Nutzt die Kategorien als Gesprächsanlass: Was hast du heute im Bereich Wissen gesehen?")
                    Text("Idee und Autor: \(Brand.author) · ein SideKids-Projekt").font(.footnote).foregroundStyle(.secondary)
                    Text("Datenschutz: Alles bleibt auf dem Gerät. Keine Konten, keine Werbung, kein Tracking. Die App richtet sich auch an Kinder unter 13 und erhebt keine personenbezogenen Daten von ihnen.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                .padding(20)
            }
            .navigationTitle("Warum SideTube?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fertig") { dismiss() } } }
        }
    }

    private func block(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.headline).accessibilityAddTraits(.isHeader)
            Text(text).font(.body).foregroundStyle(.primary)
        }
    }
}
