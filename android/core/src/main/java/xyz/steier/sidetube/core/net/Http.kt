// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.net

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Antwort einer Abfrage; der Text ist bereits als UTF-8 gelesen. */
data class HttpResponse(val status: Int, val body: String) {
    val isSuccess: Boolean get() = status in 200..299
}

/**
 * Schmale Abstraktion ueber GET. Drei Endpunkte mit je einem Aufruf rechtfertigen keine
 * Netzbibliothek; die Schnittstelle laesst sich im Test ohne Netz ersetzen.
 */
interface HttpClient {
    suspend fun get(url: String, headers: Map<String, String>): HttpResponse
}

suspend fun HttpClient.get(url: String): HttpResponse = get(url, emptyMap())

class UrlConnectionHttpClient(
    private val timeoutMillis: Int = 15_000,
    private val maxBytes: Int = 4 * 1024 * 1024,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : HttpClient {

    /**
     * Die Abfrage blockiert, deshalb der Wechsel auf einen Hintergrund-Dispatcher: Android
     * verbietet Netzzugriff auf dem Hauptthread, und die Oberflaeche soll ohnehin nicht warten.
     */
    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse = withContext(dispatcher) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            instanceFollowRedirects = true
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            // Begrenzt lesen: Eine Kanalseite ist ueber ein Megabyte gross, und ein Geraet mit
            // wenig Speicher soll an einer unerwartet grossen Antwort nicht scheitern.
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val buffer = CharArray(8 * 1024)
                val text = StringBuilder()
                while (text.length < maxBytes) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    text.appendRange(buffer, 0, read)
                }
                text.toString()
            }.orEmpty()
            HttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }
}
