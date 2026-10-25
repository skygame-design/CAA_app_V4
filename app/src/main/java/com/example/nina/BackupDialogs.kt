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

/* Sauvegardes automatiques et grilles prêtes. */

/**
 * Liste des sauvegardes automatiques (une par jour, 7 jours) avec un bouton pour revenir à l'une d'elles.
 * Restaurer demande une confirmation ; la disposition actuelle reste récupérable (⋮ › Revenir à la disposition précédente).
 */
@Composable
fun AutoBackupDialog(onDismiss: () -> Unit, onRestore: (NinaBackup) -> Unit) {
    val context = LocalContext.current
    var backups by remember { mutableStateOf<List<AutoBackupInfo>?>(null) }
    var toConfirm by remember { mutableStateOf<AutoBackupInfo?>(null) }
    LaunchedEffect(Unit) { backups = withContext(Dispatchers.IO) { AutoBackup.list(context) } }
    val dayFormat = remember { java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH) }
    val today = java.time.LocalDate.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF0F0F5),
        title = { Text("🗂 Sauvegardes automatiques", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Nina enregistre la grille chaque jour d'utilisation, dans la tablette, et garde les ${AutoBackup.KEEP_DAYS} derniers jours. " +
                    "Si la grille est abîmée, elle est récupérée automatiquement.",
                    fontSize = 14.sp, color = Color.DarkGray
                )
                when (val list = backups) {
                    null -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else -> if (list.isEmpty()) Text("Aucune sauvegarde pour le moment.", color = Color.Gray)
                    else list.forEach { info ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val label = if (info.date == today) "Aujourd'hui" else info.date.format(dayFormat).replaceFirstChar { it.uppercase() }
                                Text(label, fontWeight = FontWeight.Bold)
                                Text("${info.wordCount} mots", fontSize = 12.sp, color = Color.Gray)
                            }
                            if (toConfirm == info) {
                                TextButton(onClick = { toConfirm = null }) { Text("Annuler") }
                                Button(onClick = { AutoBackup.read(info.file)?.let(onRestore) }) { Text("Confirmer") }
                            } else {
                                OutlinedButton(onClick = { toConfirm = info }) { Text("Revenir à cette grille") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer", color = Color(0xFF4A5D91)) } }
    )
}
/**
 * Choix d'une grille prête (Premiers mots, Vocabulaire courant, Vocabulaire étendu), avec un aperçu :
 * mots de l'accueil dans leur disposition réelle, dossiers et nombre de mots, phrases rapides.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VocabularyPackDialog(currentColumns: Int, onDismiss: () -> Unit, onApply: (VocabularyPack) -> Unit) {
    var selected by remember { mutableStateOf(VocabularyPacks.all.first()) }
    val preview = remember(selected) { selected.build() }
    val accent = Color(0xFF4A5D91)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        HideSystemBarsInDialog()
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF0F0F5)) {
            Column(modifier = Modifier.systemBarsPadding()) {
                TopAppBar(
                    title = { Text("Grilles prêtes", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Fermer") } },
                    actions = {
                        Button(onClick = { onApply(selected) }, colors = ButtonDefaults.buttonColors(containerColor = accent), modifier = Modifier.padding(end = 12.dp)) {
                            Text("Installer « ${selected.name} »")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                Row(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    // Les grilles proposées
                    Column(modifier = Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        VocabularyPacks.all.forEach { pack ->
                            val isSelected = pack == selected
                            Card(
                                onClick = { selected = pack },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFE3E7F5) else Color.White),
                                border = BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) accent else Color.LightGray)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(pack.level.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = accent)
                                    Text(pack.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(pack.description, fontSize = 13.sp, color = Color.DarkGray, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        Text(
                            "Les grilles remplacent tous les pictos actuels. La disposition actuelle est gardée : " +
                            "⋮ › « Revenir à la disposition précédente » la remet en place.",
                            fontSize = 12.sp, color = Color.Gray
                        )
                    }

                    // Aperçu de la grille sélectionnée
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val total = VocabularyPacks.wordCount(preview)
                        Text(
                            "$total mots · ${preview.buttons.size} à l'accueil · ${preview.coreWords.orEmpty().size} dossiers · " +
                            "${selected.columns} pictos par rangée" + if (selected.columns != currentColumns) " (actuellement $currentColumns)" else "",
                            fontWeight = FontWeight.Bold, color = accent
                        )
                        HelpCard("Accueil (mots essentiels, dans leur disposition)") {
                            preview.buttons.chunked(selected.columns).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                                    row.forEach { cell ->
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.weight(1f).height(36.dp).background(Color(cell.colorLong), RoundedCornerShape(8.dp))
                                        ) { Text(cell.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                                    }
                                    repeat(selected.columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }
                        HelpCard("Dossiers (colonne de gauche)") {
                            preview.coreWords.orEmpty().forEach { folder ->
                                val subFolders = folder.content.filter { it.isFolder }
                                val detail = if (subFolders.isEmpty()) folder.content.joinToString(", ") { it.label }
                                             else subFolders.joinToString(" · ") { "${it.label} (${it.content.size})" }
                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text("📂 ${folder.label}", fontWeight = FontWeight.Bold, modifier = Modifier.width(160.dp))
                                    Text(detail, fontSize = 13.sp, color = Color.DarkGray, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        HelpCard("Phrases rapides (colonne de droite)") {
                            Text(preview.quickFires.orEmpty().joinToString(" · ") { it.label }, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
