// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.format.TextStyle
import java.util.Locale
import xyz.steier.sidetube.R
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.WatchHistoryEntity
import xyz.steier.sidetube.core.repo.WatchStats

/**
 * Nutzung je Profil wie `WatchStatsView` auf iOS: Zeitraum, Gesamtzeit, Balken je Tag, meistgesehene
 * Videos. Liest den Verlauf ueber [laden] (Profil, ab Zeitpunkt); gerechnet wird in [WatchStats].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchStatsScreen(
    profile: KidProfileEntity,
    laden: suspend (profileId: String, since: Long) -> List<WatchHistoryEntity>,
    onBack: () -> Unit
) {
    var periodIndex by remember { mutableIntStateOf(1) }
    val periodDays = WatchStats.PERIODS[periodIndex]
    var entries by remember { mutableStateOf<List<WatchHistoryEntity>?>(null) }
    LaunchedEffect(profile.id, periodDays) {
        // Grosszuegig ab 31 Tagen lesen; die Zuordnung zu Tagen macht WatchStats in der Gerätezone.
        val since = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
        entries = runCatching { laden(profile.id, since) }.getOrDefault(emptyList())
    }
    val stats = remember(entries, periodDays) { WatchStats.stats(entries.orEmpty(), periodDays) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nutzung), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zurueck)) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).fillMaxSize()) {
            Text(profile.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val labels = listOf(R.string.stats_heute, R.string.stats_7_tage, R.string.stats_30_tage)
                labels.forEachIndexed { i, res ->
                    SegmentedButton(
                        selected = periodIndex == i, onClick = { periodIndex = i },
                        shape = SegmentedButtonDefaults.itemShape(i, labels.size)
                    ) { Text(stringResource(res), maxLines = 1) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Zeile(stringResource(R.string.sehzeit), formatSeconds(stats.totalSeconds))
            Zeile(stringResource(R.string.kid_abschnitt_videos), stats.videoCount.toString())
            profile.dailyLimitMinutes?.let { Zeile(stringResource(R.string.tageslimit), stringResource(R.string.stats_minuten, it)) }
            Text(stringResource(R.string.stats_hinweis), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))

            if (stats.days.size > 1) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.pro_tag), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                TagesBalken(stats.days)
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.am_meisten_gesehen), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            if (entries != null && stats.topVideos.isEmpty()) {
                Text(stringResource(R.string.nichts_geschaut), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.nichts_geschaut_text), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            } else {
                stats.topVideos.forEach { video ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.stats_video_zeile, formatSeconds(video.seconds), video.plays),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Zeile(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** „2 h 15 min“ oder „15 min“, wie auf iOS. */
@Composable
private fun formatSeconds(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) stringResource(R.string.stats_stunden, hours, minutes) else stringResource(R.string.stats_minuten, minutes)
}

/** Balken je Tag, von unten wachsend; bei bis zu sieben Tagen mit Wochentag darunter. */
@Composable
private fun TagesBalken(days: List<WatchStats.Day>) {
    val maximum = (days.maxOfOrNull { it.seconds } ?: 0).coerceAtLeast(1)
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(if (days.size > 14) 2.dp else 4.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            val beschreibung = "${day.date} ${formatSeconds(day.seconds)}"
            Column(Modifier.weight(1f).semantics { contentDescription = beschreibung }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                val anteil = day.seconds.toFloat() / maximum
                Spacer(Modifier.weight((1f - anteil).coerceAtLeast(0.001f)))
                Spacer(
                    Modifier.fillMaxWidth().weight(anteil.coerceAtLeast(0.02f))
                        .background(if (day.seconds > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                )
                if (days.size <= 7) {
                    Text(day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
