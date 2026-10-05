// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.launch
import xyz.steier.sidetube.core.elternkanal.Elternkanal
import xyz.steier.sidetube.core.elternkanal.ElternkanalAblage
import xyz.steier.sidetube.core.elternkanal.Meldung
import xyz.steier.sidetube.core.elternkanal.TalkBot
import xyz.steier.sidetube.core.elternkanal.TalkBotMelder
import xyz.steier.sidetube.core.elternkanal.UngueltigerCode
import java.util.concurrent.Executors

/** Einrichtung des Elternkanals (ADR 0005), ohne Oberflaeche testbar – dieselben Texte wie iOS. */
class ElternkanalEinrichtung(private val ablage: ElternkanalAblage, private val melder: TalkBotMelder) {

    val kanal: Elternkanal? get() = ablage.lade()

    /** Ein ungueltiger Code aendert nichts an einer bestehenden Einrichtung. */
    fun uebernimm(code: String): String = try {
        ablage.speichere(Elternkanal.lies(code))
        "Eingerichtet. Mit „Test senden“ prüfen, ob die Meldung ankommt."
    } catch (e: UngueltigerCode) {
        e.message ?: "Der Einrichtungscode passt nicht."
    }

    suspend fun teste(): String {
        val kanal = kanal ?: return "Noch nicht eingerichtet."
        val text = kanal.erwaehnen.joinToString(" ") { TalkBot.erwaehnung(it) } + " SideTube: Test der Benachrichtigung"
        return when (val meldung = melder.sende(text, kanal)) {
            Meldung.Gesendet -> "Gesendet. Die Meldung erscheint in der Nextcloud-App."
            Meldung.NichtEingerichtet -> "Noch nicht eingerichtet."
            is Meldung.Gescheitert -> when (meldung.status) {
                null -> "Nicht angekommen: Server nicht erreichbar."
                401 -> "Abgelehnt: Schlüssel oder Bot passen nicht (HTTP 401)."
                404 -> "Nicht gefunden: Gespräch oder Talk fehlt (HTTP 404)."
                else -> "Nicht angekommen (HTTP ${meldung.status})."
            }
        }
    }

    fun entferne(): String {
        ablage.loesche()
        return "Entfernt. SideTube meldet keine Wünsche mehr."
    }
}

/** Einstellungen → Eltern benachrichtigen. Liegt hinter der PIN. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElternkanalScreen(einrichtung: ElternkanalEinrichtung, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kanal by remember { mutableStateOf(einrichtung.kanal) }
    var meldung by remember { mutableStateOf<String?>(null) }
    var sendet by remember { mutableStateOf(false) }
    var scannt by remember { mutableStateOf(false) }
    var entfernen by remember { mutableStateOf(false) }

    fun uebernimm(code: String) {
        meldung = einrichtung.uebernimm(code)
        kanal = einrichtung.kanal
    }

    val kameraErlaubnis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { erlaubt ->
        if (erlaubt) scannt = true else meldung = "Ohne Kamera: den Code kopieren und „Code einfügen“ wählen."
    }

    if (scannt) {
        QrScanner(onCode = { scannt = false; uebernimm(it) }, onCancel = { scannt = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eltern benachrichtigen", maxLines = 1) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Zurück") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Bei jedem neuen Wunsch schreibt SideTube eine kurze Nachricht in ein Gespräch auf der eigenen " +
                    "Nextcloud – ohne Namen und ohne Titel. Die Nextcloud-App meldet sie auf dem Telefon der Eltern.",
                style = MaterialTheme.typography.bodySmall
            )
            val aktuell = kanal
            if (aktuell != null) {
                Text("Eingerichtet", style = MaterialTheme.typography.titleSmall)
                Text("Nextcloud: " + aktuell.server.removePrefix("https://"))
                Text("Erwähnt: " + aktuell.erwaehnen.joinToString(" ") { TalkBot.erwaehnung(it) })
                Button(
                    onClick = { sendet = true; scope.launch { meldung = einrichtung.teste(); sendet = false } },
                    enabled = !sendet,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (sendet) "Sendet …" else "Test senden") }
                OutlinedButton(onClick = { kameraOderScan(context, { scannt = true }) { kameraErlaubnis.launch(Manifest.permission.CAMERA) } },
                    modifier = Modifier.fillMaxWidth()) { Text("Neu einrichten") }
                OutlinedButton(onClick = { entfernen = true }, modifier = Modifier.fillMaxWidth()) { Text("Entfernen") }
            } else {
                Button(onClick = { kameraOderScan(context, { scannt = true }) { kameraErlaubnis.launch(Manifest.permission.CAMERA) } },
                    modifier = Modifier.fillMaxWidth()) { Text("Einrichtungscode scannen") }
                OutlinedButton(onClick = { uebernimm(zwischenablage(context)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Code einfügen")
                }
                Text(
                    "Den Code erzeugt das Skript talk-wunschkanal.sh auf dem Server der Familie (Nextcloud mit Talk).",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            meldung?.let { Text(it, modifier = Modifier.testTag("elternkanal.meldung")) }
        }
    }

    if (entfernen) {
        AlertDialog(
            onDismissRequest = { entfernen = false },
            title = { Text("Benachrichtigung entfernen?") },
            text = { Text("Der Schlüssel wird von diesem Gerät gelöscht. Bot und Gespräch auf der Nextcloud bleiben.") },
            confirmButton = {
                TextButton(onClick = { entfernen = false; meldung = einrichtung.entferne(); kanal = null }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { entfernen = false }) { Text("Abbrechen") } }
        )
    }
}

private fun kameraOderScan(context: Context, scan: () -> Unit, fragen: () -> Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scan() else fragen()
}

private fun zwischenablage(context: Context): String {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    return clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}

/** Kamerabild mit QR-Erkennung (CameraX + ZXing). Das Bild verlaesst das Geraet nicht. */
@Composable
private fun QrScanner(onCode: (String) -> Unit, onCancel: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val analyse = remember { Executors.newSingleThreadExecutor() }
    var fertig by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { analyse.shutdown() } }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val vorschau = PreviewView(ctx)
                val anbieter = ProcessCameraProvider.getInstance(ctx)
                anbieter.addListener({
                    val kamera = anbieter.get()
                    val bild = Preview.Builder().build().also { it.surfaceProvider = vorschau.surfaceProvider }
                    val erkennung = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    erkennung.setAnalyzer(analyse) { proxy ->
                        val text = proxy.use(::lesen)
                        if (text != null && !fertig) {
                            fertig = true
                            ContextCompat.getMainExecutor(ctx).execute { kamera.unbindAll(); onCode(text) }
                        }
                    }
                    kamera.unbindAll()
                    kamera.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, bild, erkennung)
                }, ContextCompat.getMainExecutor(ctx))
                vorschau
            }
        )
        TextButton(onClick = {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            onCancel()
        }, modifier = Modifier.padding(16.dp)) { Text("Abbrechen") }
    }
}

private val leser = QRCodeReader()
private val hinweise = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)

/** Helligkeitsebene (Y) des Kamerabilds an ZXing; null, solange kein Code erkannt ist. */
private fun lesen(proxy: ImageProxy): String? {
    val ebene = proxy.planes.firstOrNull() ?: return null
    val puffer = ebene.buffer
    val daten = ByteArray(puffer.remaining()).also { puffer.get(it) }
    val quelle = PlanarYUVLuminanceSource(daten, ebene.rowStride, proxy.height, 0, 0, proxy.width, proxy.height, false)
    return runCatching { leser.decode(BinaryBitmap(HybridBinarizer(quelle)), hinweise).text }.getOrNull()
        .also { leser.reset() }
}
