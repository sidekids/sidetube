// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.
package xyz.steier.sidetube.kid

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.provider.YouTubeThumbnails
import java.net.HttpURLConnection
import java.net.URL

/**
 * Laedt Vorschaubilder fuer die Kinderliste.
 *
 * Bis hierhin zeigte die Liste nur Platzhalter, obwohl die Eintraege ihre Bildadressen
 * kannten (siehe docs/privacy/network-services.md). Eine Bildbibliothek lohnt fuer kleine
 * Kacheln nicht: ein GET, verkleinert dekodiert, im Arbeitsspeicher gehalten.
 *
 * Nur die Bildserver, die die Datenschutzdoku nennt. Eine fremde Adresse aus den Metadaten
 * fuehrt so nicht zu einer Anfrage an einen beliebigen Server.
 */
object Vorschaubilder {
    private val ERLAUBT = setOf("i.ytimg.com", "yt3.ggpht.com", "yt3.googleusercontent.com")
    private const val MAX_BYTES = 1024 * 1024
    private const val ZEITLIMIT_MS = 10_000

    /** Rund 8 MB Bilder: genug fuer eine Liste, ohne ein kleines Geraet zu belasten. */
    private val speicher = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun ausDemSpeicher(url: String): ImageBitmap? = speicher.get(url)

    /** `mqdefault` (320 x 180) reicht fuer die kleinen Kacheln. */
    private const val BREITE_PX = 320

    /**
     * Bildadresse eines YouTube-Videos aus seiner Kennung. Der Kanal-Feed liefert zwar eigene
     * Adressen, aber auf `i1.ytimg.com` bis `i4.ytimg.com`; die stehen nicht in [ERLAUBT], und
     * die Kanalansicht blieb deshalb bei Platzhaltern.
     */
    fun fuerVideo(videoId: String): String = YouTubeThumbnails.url(videoId, BREITE_PX)

    /**
     * Bildadresse eines Listeneintrags. Eintraege aus Startpaketen tragen keine; bei
     * YouTube-Videos ergibt sie sich aus der Kennung, wie auf iOS.
     */
    fun adresse(item: WhitelistItemEntity): String? =
        item.thumbnailUrl.takeIf { it.isNotBlank() }
            ?: fuerVideo(item.contentId)
                .takeIf { item.type == WhitelistItemType.VIDEO.name && item.provider == "youtube" }

    suspend fun lade(url: String, breitePx: Int): ImageBitmap? {
        speicher.get(url)?.let { return it }
        val adresse = runCatching { URL(url) }.getOrNull() ?: return null
        if (adresse.protocol != "https" || adresse.host !in ERLAUBT) return null
        val bild = withContext(Dispatchers.IO) {
            runCatching {
                val verbindung = (adresse.openConnection() as HttpURLConnection).apply {
                    connectTimeout = ZEITLIMIT_MS
                    readTimeout = ZEITLIMIT_MS
                }
                try {
                    if (verbindung.responseCode !in 200..299) return@runCatching null
                    // Begrenzt lesen; readNBytes gibt es erst ab API 33, das SP-01 hat 31.
                    val daten = verbindung.inputStream.use { ein ->
                        val aus = java.io.ByteArrayOutputStream()
                        val puffer = ByteArray(16 * 1024)
                        while (aus.size() < MAX_BYTES) {
                            val n = ein.read(puffer)
                            if (n <= 0) break
                            aus.write(puffer, 0, n)
                        }
                        aus.toByteArray()
                    }
                    // Erst die Masse lesen, dann verkleinert dekodieren: hqdefault misst 480 px,
                    // die Kachel braucht ein Drittel davon.
                    val masse = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(daten, 0, daten.size, masse)
                    var faktor = 1
                    while (masse.outWidth / (faktor * 2) >= breitePx) faktor *= 2
                    val optionen = BitmapFactory.Options().apply { inSampleSize = faktor }
                    BitmapFactory.decodeByteArray(daten, 0, daten.size, optionen)?.asImageBitmap()
                } finally {
                    verbindung.disconnect()
                }
            }.getOrNull()
        }
        if (bild != null) speicher.put(url, bild)
        return bild
    }
}
