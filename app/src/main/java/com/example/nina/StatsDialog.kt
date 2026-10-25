package com.example.nina

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

/* Statistiques d'utilisation et export CSV. */

/** Périodes proposées pour les statistiques (nombre de jours avant aujourd'hui, null = tout). */
private enum class StatsPeriod(val label: String, val daysBefore: Long?) {
    TODAY("Aujourd'hui", 0), WEEK("7 jours", 6), MONTH("30 jours", 29), ALL("Tout", null)
}
/**
 * Dialogue des statistiques d'utilisation, pensé pour le suivi avec l'orthophoniste :
 * mots les plus utilisés par période, nombre de mots par jour, phrases prononcées, export CSV.
 * "Tout" reprend aussi les compteurs enregistrés avant l'ajout du journal daté.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDialog(
    stats: Map<String, Int>,
    onDismiss: () -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<UsageEvent>>(emptyList()) }
    LaunchedEffect(Unit) { events = withContext(Dispatchers.IO) { UsageLog.read(context) } }

    var period by remember { mutableStateOf(StatsPeriod.WEEK) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }

    val inPeriod = UsageLog.since(events, period.daysBefore?.let { java.time.LocalDate.now().minusDays(it) })
    val words = if (period == StatsPeriod.ALL) stats.entries.sortedByDescending { it.value }.map { it.key to it.value }
                else UsageLog.wordCounts(inPeriod)
    val sentences = inPeriod.filter { it.type == UsageType.SENTENCE }.reversed()
    val daily = UsageLog.dailyWordCounts(events, if (period == StatsPeriod.MONTH || period == StatsPeriod.ALL) 30 else 14)
    val accent = Color(0xFF4A5D91)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF0F0F5),
        title = { Text("📊 Statistiques d'utilisation", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Période
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsPeriod.entries.forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) })
                    }
                }

                // Chiffres clés
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    StatTile("Mots", words.sumOf { it.second }.toString(), Modifier.weight(1f))
                    StatTile("Mots différents", words.size.toString(), Modifier.weight(1f))
                    StatTile("Phrases", sentences.size.toString(), Modifier.weight(1f))
                }

                PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, contentColor = accent) {
                    listOf("Mots", "Par jour", "Phrases").forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(260.dp).padding(top = 12.dp)) {
                    when (selectedTab) {
                        0 -> if (words.isEmpty()) EmptyStats() else {
                            val max = words.first().second.coerceAtLeast(1)
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(words) { (word, count) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(word, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.width(120.dp))
                                        Box(modifier = Modifier.weight(1f).height(18.dp)) {
                                            Box(
                                                modifier = Modifier.fillMaxHeight().fillMaxWidth(count / max.toFloat())
                                                    .background(accent.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                            )
                                        }
                                        Text("$count", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                                    }
                                }
                            }
                        }
                        1 -> if (daily.all { it.second == 0 }) EmptyStats() else {
                            val max = daily.maxOf { it.second }.coerceAtLeast(1)
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                daily.forEach { (day, count) ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom,
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    ) {
                                        if (count > 0) Text("$count", fontSize = 10.sp, color = Color.Gray, maxLines = 1)
                                        Box(
                                            modifier = Modifier.fillMaxWidth(0.75f).height(190.dp * (count / max.toFloat()))
                                                .background(accent, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        )
                                        Text("${day.dayOfMonth}", fontSize = 10.sp, color = Color.Gray, maxLines = 1)
                                    }
                                }
                            }
                        }
                        else -> if (sentences.isEmpty()) EmptyStats() else {
                            val format = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(sentences) { e ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(e.time.format(format), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(90.dp))
                                        Text(e.text, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD1D5E3))) {
                Text("Fermer", color = accent)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    if (events.isEmpty()) {
                        android.widget.Toast.makeText(context, "Aucune donnée datée à exporter", android.widget.Toast.LENGTH_SHORT).show()
                    } else scope.launch {
                        val file = withContext(Dispatchers.IO) { UsageLog.exportCsv(context, events) }
                        shareStatsFile(context, file)
                    }
                }) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exporter (CSV)", color = accent)
                }
                // Remise à zéro en deux temps pour éviter une perte de données par erreur
                TextButton(onClick = {
                    if (confirmReset) { onReset(); events = emptyList(); confirmReset = false } else confirmReset = true
                }) {
                    Text(if (confirmReset) "Confirmer l'effacement ?" else "Remettre à zéro", color = Color.Red)
                }
            }
        }
    )
}
@Composable
private fun StatTile(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Black, fontSize = 28.sp, color = Color(0xFF1565C0))
            Text(title, color = Color(0xFF1565C0), fontSize = 12.sp)
        }
    }
}
@Composable
private fun EmptyStats() {
    Text("Aucune donnée pour cette période.", color = Color.Gray)
}
/** Partage le fichier CSV (mail, Drive, messagerie...) pour l'envoyer à l'orthophoniste. */
private fun shareStatsFile(context: android.content.Context, file: java.io.File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Statistiques Nina")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Exporter les statistiques via :"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
