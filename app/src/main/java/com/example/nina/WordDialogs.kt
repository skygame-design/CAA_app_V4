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

/* Mots : formes du mot (appui long), saisie libre au clavier, recherche d'un mot (modélisation). */

/**
 * Formes d'un mot, ouvertes par un appui long (comme les "Word Forms" de TD Snap) :
 * conjugaisons d'un verbe, ou singulier / pluriel d'un nom. Toucher une forme l'ajoute à la phrase.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordFormsDialog(cell: AACCell, forms: List<WordForm>, onPick: (WordForm) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF0F0F5),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!cell.imageUrl.isNullOrEmpty() && cell.imageUrl != "FETCH") {
                    AsyncImage(model = cell.imageUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(cell.label, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                forms.forEach { form ->
                    Card(
                        onClick = { onPick(form) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(cell.colorLong)),
                        border = BorderStroke(2.dp, Color.White)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.widthIn(min = 110.dp).padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(form.text, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.DarkGray)
                            form.hint?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel), color = Color(0xFF4A5D91)) }
        }
    )
}
/**
 * Dialogue permettant la saisie libre via un clavier Android standard.
 * Propose des prédictions basées sur les pictogrammes ARASAAC au fur et à mesure de la frappe.
 */
@Composable
fun KeyboardInputDialog(
    onDismiss: () -> Unit,
    onValidate: (AACCell) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // Pair(Mot, Url)

    // On isole le mot actuellement en cours de frappe
    val words = text.split(" ")
    val lastWord = words.lastOrNull() ?: ""

    LaunchedEffect(lastWord) {
        if (lastWord.length >= 2) { // On cherche à partir de 2 lettres
            val searchResult = try {
                val results = RetrofitInstance.arasaacApi.searchPictograms(lastWord.lowercase().trim())
                // On prend les 4 premiers résultats
                results.take(4).map {
                    Pair(lastWord, "https://static.arasaac.org/pictograms/${it._id}/${it._id}_300.png")
                }
            } catch (_: Exception) {
                emptyList()
            }
            predictions = searchResult
        } else {
            predictions = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF0F0F5),
        title = { Text("Clavier & Prédiction", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Barre de prédictions visuelles
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (predictions.isEmpty() && lastWord.length >= 2) {
                        item { Text("...", color = Color.Gray) }
                    } else {
                        items(predictions) { entry ->
                            val (word, url) = entry
                            Card(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clickable {
                                        // Auto-complétion : on remplace le mot en cours par le mot complet et on ajoute un espace
                                        val newText = text.dropLast(lastWord.length) + word + " "
                                        text = newText
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFF4A5D91))
                            ) {
                                Box(modifier = Modifier.padding(4.dp).fillMaxSize()) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }
                }

                // Zone de texte principale
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Écris ton message ici...") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        // On crée un bouton "Texte Libre" avec la phrase entière
                        onValidate(AACCell(label = text.trim(), colorLong = 0xFFFFFFFFL, imageUrl = null))
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD1D5E3))
            ) {
                Text("Ajouter à la phrase", color = Color(0xFF4A5D91))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = Color.Gray) }
        }
    )
}
/**
 * Dialogue de recherche de mots.
 * Permet de trouver le chemin d'accès d'un pictogramme dans l'arborescence des dossiers.
 */
@Composable
fun WordFinderDialog(
    allButtons: List<AACCell>,
    coreWords: List<AACCell>,
    quickFires: List<AACCell>,
    onShow: (WordGuide) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults = remember(searchQuery, allButtons, coreWords, quickFires) {
        findWordGuides(searchQuery, allButtons, coreWords, quickFires)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF0F0F5),
        title = {
            Text("Trouver un mot", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Ex: Toboggan, Pomme...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (searchQuery.isNotBlank()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (searchResults.isEmpty()) {
                            item { Text("Aucun mot trouvé.", color = Color.Gray) }
                        } else {
                            items(searchResults) { guide ->
                                val cell = guide.cell
                                Card(
                                    onClick = { onShow(guide) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = cell.imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                            Text(cell.label.uppercase(), fontWeight = FontWeight.Bold)
                                            Text(guide.place, fontSize = 12.sp, color = Color.Gray)
                                        }
                                        // Montre le chemin sur la grille, case par case (comme "Find Word" de TD Snap)
                                        FilledTonalButton(onClick = { onShow(guide) }) {
                                            Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Montrer")
                                        }
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
                Text("Fermer", color = Color(0xFF4A5D91))
            }
        }
    )
}

// --- AIDE & TUTORIEL ---
