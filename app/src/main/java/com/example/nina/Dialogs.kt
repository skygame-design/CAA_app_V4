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

enum class EditTab { APPARENCE, AUDIO, DOSSIER, DANGER }

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFF4A5D91), // Bleu principal
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

/**
 * Les fenêtres plein écran (aide, création de picto…) ont leur propre fenêtre Android :
 * sans cela, la barre d'état et la barre de navigation réapparaissent quand elles s'ouvrent.
 */
@Composable
fun HideSystemBarsInDialog() {
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { hideSystemBars(it) }
    }
}

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

/**
 * Recherche d'images dans toutes les banques (ARASAAC, Mulberry, Sclera, Tawasol, Twemoji…),
 * avec un filtre par banque et la licence de l'image choisie (à respecter et créditer).
 * L'image choisie est téléchargée dans la tablette à l'enregistrement du picto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PictoSearchPanel(query: String, selectedUrl: String?, onSelect: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<PictoResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    val accent = Color(0xFF4A5D91)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (query.isBlank() || isLoading) return@Button
                scope.launch {
                    isLoading = true; sourceFilter = null
                    results = PictoSearch.search(query)
                    isLoading = false; searched = true
                }
            },
            enabled = query.isNotBlank(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
            else {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Chercher « ${query.trim().ifEmpty { "…" }} » dans les banques d'images", fontSize = 12.sp)
            }
        }

        if (searched && results.isEmpty()) {
            Text("Aucune image trouvée (ou pas de réseau). Essayez un autre mot, ou une photo de la galerie.", fontSize = 12.sp, color = Color.Gray)
        }

        if (results.isNotEmpty()) {
            // Filtre par banque, avec le nombre d'images
            val counts = results.groupingBy { it.source }.eachCount()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = sourceFilter == null, onClick = { sourceFilter = null }, label = { Text("Toutes (${results.size})") })
                counts.forEach { (source, count) ->
                    FilterChip(selected = sourceFilter == source, onClick = { sourceFilter = source }, label = { Text("$source ($count)") })
                }
            }
            val shown = results.filter { sourceFilter == null || it.source == sourceFilter }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown) { res ->
                    val selected = selectedUrl == res.imageUrl
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                        AsyncImage(
                            model = res.imageUrl, contentDescription = res.source,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                                .border(if (selected) 3.dp else 1.dp, if (selected) accent else Color.LightGray, RoundedCornerShape(8.dp))
                                .clickable { onSelect(res.imageUrl) }.padding(4.dp)
                        )
                        Text(res.source, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
                    }
                }
            }
            results.firstOrNull { it.imageUrl == selectedUrl }?.let {
                Text("Image : ${it.source} — licence ${it.licence}", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

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
 * Champ optionnel "Texte prononcé" : ce que Nina dit quand on touche le picto.
 * Vide = le texte du picto est prononcé.
 */
@Composable
fun SpokenTextField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Texte prononcé (optionnel)") },
        placeholder = { Text(label.ifBlank { "Ex. : J'ai besoin d'aller aux toilettes" }, color = Color.LightGray) },
        supportingText = { Text("Dit à voix haute quand on touche le picto. La phrase garde le texte du picto.") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

/**
 * Dialogue de création d'un nouveau bouton.
 * Permet de définir le nom, l'image (galerie ou ARASAAC), la couleur et le type (dossier, phrase rapide).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddButtonDialog(onDismiss: () -> Unit, onConfirm: (AACCell) -> Unit) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(value = "") }
    var spokenText by remember { mutableStateOf(value = "") }
    var isFolder by remember { mutableStateOf(value = false) }
    var isQuickPhrase by remember { mutableStateOf(value = false) }
    var isHidden by remember { mutableStateOf(value = false) }
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    var selectedColor by remember { mutableLongStateOf(0xFFFFD54FL) }
    var isSaving by remember { mutableStateOf(value = false) }

    var audioPath by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    val recorder = remember { AudioRecorder(context) }
    val scope = rememberCoroutineScope()

    // Lanceur pour choisir une image dans la galerie
    val photoLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                selectedUrl = saveImageToInternalStorage(context, it)
            }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        HideSystemBarsInDialog()
        Surface(
            modifier = Modifier.fillMaxSize().imePadding(), // Laisse la place au clavier
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Nouveau picto", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer")
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                isSaving = true
                                scope.launch(Dispatchers.IO) {
                                    // Image du web : enregistrée dans la tablette ; si le téléchargement échoue, on garde l'adresse (réessai au démarrage)
                                    val remote = selectedUrl?.takeIf { isRemoteImage(it) }
                                    val finalUrl = if (remote != null) saveImageLocally(context, remote, "picto_${System.currentTimeMillis()}") ?: remote else selectedUrl
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        onConfirm(AACCell(label = label, colorLong = selectedColor, imageUrl = finalUrl, audioPath = audioPath, isFolder = isFolder, isQuickPhrase = isQuickPhrase, isHidden = isHidden, spokenText = spokenText.trim().ifBlank { null }, sourceUrl = remote))
                                    }
                                }
                            },
                            enabled = (label.isNotEmpty() && (selectedUrl != null) && !isSaving),
                            modifier = Modifier.padding(end = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            else Text("Créer")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsSectionTitle("Identification")
                    SettingsCard {
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("Texte du picto") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        SpokenTextField(value = spokenText, label = label, onValueChange = { spokenText = it })
                    }

                    SettingsSectionTitle("Apparence")
                    SettingsCard {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(selectedColor))
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
                                    .clickable { photoLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!selectedUrl.isNullOrEmpty()) {
                                    AsyncImage(model = selectedUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                                } else {
                                    Icon(Icons.Default.AddAPhoto, null, tint = Color.Gray)
                                }
                                Icon(Icons.Default.Add, null, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp), tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Image du picto", fontWeight = FontWeight.Bold)
                                Text("Touchez le carré pour une photo de la galerie, ou cherchez dans les banques d'images.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        PictoSearchPanel(query = label, selectedUrl = selectedUrl, onSelect = { selectedUrl = it })
                    }

                    SettingsSectionTitle("Catégorie Fitzgerald")
                    SettingsCard {
                        val fitzgeraldThemes = listOf(
                            Pair(Color(0xFFFFD54F), "Personnes (Sujets, Pronoms)"),
                            Pair(Color(0xFF81C784), "Actions (Verbes)"),
                            Pair(Color(0xFFFFB74D), "Choses (Noms, Objets)"),
                            Pair(Color(0xFF64B5F6), "Descriptifs (Adjectifs, Sentiments)"),
                            Pair(Color(0xFFF06292), "Social (Politesse, Salutations)"),
                            Pair(Color(0xFFBA68C8), "Questions (Mots interrogatifs)"),
                            Pair(Color(0xFFE0E0E0), "Petits mots (Prépositions, Liens)")
                        )
                        fitzgeraldThemes.forEach { (color, themeLabel) ->
                            val colorLong = color.toArgb().toLong() and 0xFFFFFFFFL
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedColor = colorLong }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(1.dp, Color.LightGray, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = themeLabel,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (selectedColor == colorLong) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Sélectionné",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    SettingsSectionTitle("Comportement")
                    SettingsCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isFolder, onCheckedChange = { isFolder = it; if (it) isQuickPhrase = false })
                            Text("Dossier (Contient d'autres pictos)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isQuickPhrase, onCheckedChange = { isQuickPhrase = it; if (it) isFolder = false })
                            Text("Phrase Rapide (Action directe)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isHidden, onCheckedChange = { isHidden = it })
                            Text("Masquer (Mode Admin uniquement)")
                        }
                    }

                    SettingsSectionTitle("Voix Personnalisée")
                    SettingsCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(if (isRecording) Color.Red else MaterialTheme.colorScheme.primaryContainer).pointerInput(Unit) { detectTapGestures(onPress = { try { audioPath = recorder.startRecording("audio_${System.currentTimeMillis()}"); isRecording = true; awaitRelease() } finally { recorder.stopRecording(); isRecording = false } }) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Mic, null, tint = if (isRecording) Color.White else MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Column {
                                Text(if (isRecording) "Enregistrement en cours..." else "Maintenez pour enregistrer", fontSize = 13.sp)
                                if (audioPath != null && !isRecording) Text("Vocal prêt ✅", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialogue de modification d'un bouton existant.
 * Permet d'éditer le nom, l'image, la couleur ou de supprimer le bouton.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditDialog(
    cell: AACCell,
    allCells: List<AACCell>,
    onDelete: (AACCell) -> Unit,
    // Picto modifié (même id), déplacement demandé, et dossier cible (null = Racine)
    onSave: (AACCell, Boolean, AACCell?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(cell.label) }
    var spokenText by remember { mutableStateOf(cell.spokenText.orEmpty()) }
    var imageUrl by remember { mutableStateOf(cell.imageUrl) }
    var color by remember { mutableLongStateOf(cell.colorLong) }
    var isFolder by remember { mutableStateOf(cell.isFolder) }
    var isQuickPhrase by remember { mutableStateOf(cell.isQuickPhrase) }
    var isHidden by remember { mutableStateOf(cell.isHidden) }
    var pendingFolderMove by remember { mutableStateOf<AACCell?>(null) }
    var isMoveRequested by remember { mutableStateOf(false) }

    var audioPath by remember { mutableStateOf(cell.audioPath) }
    var isRecording by remember { mutableStateOf(false) }
    val audioRecorder = remember { AudioRecorder(context) }

    // Gestionnaire de permission pour le micro
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Si permission accordée, on lance l'enregistrement
            val safeName = name.replace(Regex("[^A-Za-z0-9]"), "_").take(10)
            audioPath = audioRecorder.startRecording(safeName)
            isRecording = true
        } else {
            android.widget.Toast.makeText(context, "Permission micro refusée", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val scope = rememberCoroutineScope()
    // Un dossier ne peut aller ni dans lui-même ni dans un de ses sous-dossiers
    val ownFolderIds = remember(cell) { allFolders(listOf(cell)).map { it.id }.toSet() + cell.id }
    val availableFolders = allCells.filter { it.isFolder && it.id !in ownFolderIds }

    val fitzgeraldThemes = listOf(
        Pair(Color(0xFFFFD54F), "Personnes (Sujets, Pronoms)"),
        Pair(Color(0xFF81C784), "Actions (Verbes)"),
        Pair(Color(0xFFFFB74D), "Choses (Noms, Objets)"),
        Pair(Color(0xFF64B5F6), "Descriptifs (Adjectifs, Sentiments)"),
        Pair(Color(0xFFF06292), "Social (Politesse, Salutations)"),
        Pair(Color(0xFFBA68C8), "Questions (Mots interrogatifs)"),
        Pair(Color(0xFFE0E0E0), "Petits mots (Prépositions, Liens)")
    )

    val photoLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                imageUrl = saveImageToInternalStorage(context, it)
            }
        }

    var selectedTab by remember { mutableStateOf(EditTab.APPARENCE) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        HideSystemBarsInDialog()
        Surface(
            modifier = Modifier
                .imePadding() // Laisse la place au clavier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(24.dp)),
            color = Color(0xFFF3F4F6) // Fond gris clair type "Paramètres"
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // En-tête fixe
                CenterAlignedTopAppBar(
                    title = { Text("Modifier le picto", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                // Barre d'onglets
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF4A5D91),
                    indicator = { 
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(selectedTab.ordinal),
                            color = Color(0xFF4A5D91)
                        )
                    }
                ) {
                    EditTab.values().forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { 
                                Text(
                                    when(tab) {
                                        EditTab.APPARENCE -> "Apparence"
                                        EditTab.AUDIO -> "Audio"
                                        EditTab.DOSSIER -> "Dossier"
                                        EditTab.DANGER -> "Suppression"
                                    },
                                    fontSize = 12.sp
                                ) 
                            },
                            icon = {
                                Icon(
                                    when(tab) {
                                        EditTab.APPARENCE -> Icons.Default.Palette
                                        EditTab.AUDIO -> Icons.AutoMirrored.Filled.VolumeUp
                                        EditTab.DOSSIER -> Icons.Default.Folder
                                        EditTab.DANGER -> Icons.Default.Delete
                                    },
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }

                // Contenu dynamique
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        EditTab.APPARENCE -> {
                            SettingsCard {
                                // Nom du picto
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Texte du picto") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                SpokenTextField(value = spokenText, label = name, onValueChange = { spokenText = it })

                                HorizontalDivider()

                                // Image et Recherche
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(color).copy(alpha = 0.1f))
                                            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                                            .clickable { photoLauncher.launch("image/*") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!imageUrl.isNullOrEmpty()) {
                                            AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                                        } else {
                                            Icon(Icons.Default.AddAPhoto, null, tint = Color.Gray)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Image du picto", fontWeight = FontWeight.Bold)
                                        Text("Touchez le carré pour une photo de la galerie, ou cherchez dans les banques d'images.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                                PictoSearchPanel(query = name, selectedUrl = imageUrl, onSelect = { imageUrl = it })

                                HorizontalDivider()

                                // Sélecteur de couleur
                                Text(
                                    "Catégorie (Code Fitzgerald)", 
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFF4A5D91),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    fitzgeraldThemes.forEach { (fitzColor, label) ->
                                        // On compare en ignorant le canal alpha pour plus de souplesse si besoin, 
                                        // mais toArgb().toLong() & 0xFFFFFFFFL est le standard ici
                                        val isSelected = color == (fitzColor.toArgb().toLong() and 0xFFFFFFFFL)
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) Color(0xFFE8EAF6) else Color.Transparent)
                                                .border(
                                                    width = if (isSelected) 2.dp else 0.dp,
                                                    color = if (isSelected) Color(0xFF4A5D91) else Color.Transparent,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable { color = fitzColor.toArgb().toLong() and 0xFFFFFFFFL }
                                                .padding(8.dp)
                                        ) {
                                            // La pastille de couleur
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(fitzColor)
                                                    .border(1.dp, Color.LightGray, CircleShape)
                                            )
                                            
                                            Spacer(modifier = Modifier.width(16.dp))
                                            
                                            // Le texte du thème
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color(0xFF4A5D91) else Color.Black
                                            )
                                            
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.weight(1f))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFF4A5D91),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        EditTab.AUDIO -> {
                            SettingsCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Audio personnalisé",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4A5D91)
                                        )
                                        Text(
                                            text = if (isRecording) "Enregistrement en cours..." else "Laissez vide pour la voix par défaut",
                                            fontSize = 12.sp,
                                            color = if (isRecording) Color.Red else Color.Gray
                                        )
                                    }

                                    // Bouton Micro / Stop
                                    IconButton(
                                        onClick = {
                                            if (isRecording) {
                                                audioRecorder.stopRecording()
                                                isRecording = false
                                            } else {
                                                recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(
                                                color = if (isRecording) Color(0xFFFFEBEE) else Color(0xFFE8EAF6),
                                                shape = CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                            contentDescription = "Enregistrer",
                                            tint = if (isRecording) Color.Red else Color(0xFF4A5D91)
                                        )
                                    }
                                }

                                // Afficher une option de suppression si un audio existe et qu'on n'enregistre pas
                                if (audioPath != null && !isRecording) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Un enregistrement est lié à ce picto.", fontSize = 12.sp, color = Color(0xFF4A5D91))
                                        TextButton(onClick = { audioPath = null }) {
                                            Text("Supprimer", color = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                        EditTab.DOSSIER -> {
                            SettingsCard {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Checkbox(checked = isFolder, onCheckedChange = { isFolder = it; if (it) isQuickPhrase = false })
                                    Text("Dossier (Contient d'autres pictos)")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Checkbox(checked = isQuickPhrase, onCheckedChange = { isQuickPhrase = it; if (it) isFolder = false })
                                    Text("Phrase Rapide (Action directe)")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Checkbox(checked = isHidden, onCheckedChange = { isHidden = it })
                                    Text("Masquer (Mode Admin uniquement)")
                                }
                                
                                HorizontalDivider()

                                if (availableFolders.isEmpty()) {
                                    Text("Aucun dossier disponible", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("Déplacer vers :", style = MaterialTheme.typography.labelSmall)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(availableFolders) { folder ->
                                            Card(
                                                onClick = { 
                                                    pendingFolderMove = folder
                                                    isMoveRequested = true
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(folder.colorLong).copy(alpha = 0.2f)),
                                                border = BorderStroke(1.dp, Color(folder.colorLong))
                                            ) {
                                                Text("📂 ${folder.label}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                        item {
                                            Card(
                                                onClick = { 
                                                    pendingFolderMove = null 
                                                    isMoveRequested = true
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.2f)),
                                                border = BorderStroke(1.dp, Color.Gray)
                                            ) {
                                                Text("Racine", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    if (isMoveRequested) {
                                        val destinationName = pendingFolderMove?.label ?: "la Racine (Accueil)"
                                        Text(
                                            text = "Sera déplacé vers : $destinationName",
                                            color = Color(0xFF4A5D91),
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        EditTab.DANGER -> {
                            SettingsCard {
                                Text("Zone de danger", fontWeight = FontWeight.Bold, color = Color.Red)
                                Text("Cette action est irréversible.", fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { onDelete(cell) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE), contentColor = Color.Red),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                    Text("Supprimer ce picto")
                                }
                            }
                        }
                    }
                }

                // Pied de page fixe (Bouton Enregistrer)
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Button(
                            onClick = {
                                val edited = cell.copy(
                                    label = name, colorLong = color, imageUrl = imageUrl, audioPath = audioPath,
                                    isFolder = isFolder, isQuickPhrase = isQuickPhrase, isHidden = isHidden,
                                    spokenText = spokenText.trim().ifBlank { null }
                                )
                                onSave(edited, isMoveRequested, pendingFolderMove)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5D91))
                        ) {
                            Text("Enregistrer les modifications", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialogue de défi parental pour accéder au mode Admin.
 * Demande une addition simple pour vérifier que l'utilisateur est un adulte.
 */
@Composable
fun AdminChallengeDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val num1 = remember { (1..10).random() }
    val num2 = remember { (1..5).random() }
    val expectedAnswer = num1 + num2
    var input by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun validateAndSubmit() {
        if (input.trim().toIntOrNull() == expectedAnswer) {
            onSuccess()
        } else {
            input = ""
            isError = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF0F0F5),
        title = {
            Text(
                stringResource(R.string.parental_lock_title),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.parental_lock_instruction)); Spacer(
                modifier = Modifier.height(
                    16.dp
                )
            )
                Text(
                    "$num1 + $num2 = ?",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF4A5D91)
                ); Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = input,
                                    onValueChange = {
                    input = it
                    if (isError) isError = false
                },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = isError,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        stringResource(R.string.parental_lock_error),
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { validateAndSubmit() },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD1D5E3))
            ) {
                Text(stringResource(R.string.btn_validate), color = Color(0xFF4A5D91))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = Color(0xFF4A5D91))
            }
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
 * Dialogue des paramètres globaux de l'application.
 * Divisé en 3 onglets : Apparence, Voix, et Tactile.
 */
@Composable
fun SettingsDialog(
    currentBg: Long,
    currentPitch: Float,
    currentRate: Float,
    currentDebounce: Long,
    currentCviMode: Boolean,
    currentAutoReturn: Boolean,
    onDismiss: () -> Unit,
    onSaveSettings: (Long, Float, Float, Long, Boolean, Boolean) -> Unit
) {
    var tempAutoReturn by remember { mutableStateOf(currentAutoReturn) }
    var selectedColor by remember { mutableLongStateOf(currentBg) }
    var tempPitch by remember { mutableFloatStateOf(currentPitch) }
    var tempRate by remember { mutableFloatStateOf(currentRate) }
    var tempDebounce by remember { mutableFloatStateOf(currentDebounce.toFloat() / 1000f) }
    var tempCviMode by remember { mutableStateOf(currentCviMode) }

    // Gestion des onglets
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("🎨 Apparence", "🗣️ Voix", "👆 Tactile")

    val suggestedColors = listOf(
        0xFFFAF3E0L, 0xFFE3F2FDL, 0xFFE8F5E9L, 0xFFFCE4ECL,
        0xFFF3E5F5L, 0xFFFFF3E0L, 0xFFF5F5F5L, 0xFFFFFFFFL
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF0F0F5),
        title = {
            Text("Paramètres", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Barre d'onglets
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF4A5D91),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 12.sp, maxLines = 1) }
                        )
                    }
                }

                // Contenu défilant selon l'onglet sélectionné
                Box(modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        0 -> { // APPARENCE
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                ) {
                                    Text("Contraste Élevé (CVI)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Switch(checked = tempCviMode, onCheckedChange = { tempCviMode = it })
                                }

                                Text("Couleur de fond :", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        suggestedColors.take(4).forEach { colorVal ->
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1.3f).clip(RoundedCornerShape(12.dp)).background(Color(colorVal))
                                                .border(if (selectedColor == colorVal) 3.dp else 1.dp, if (selectedColor == colorVal) Color(0xFF4A5D91) else Color.LightGray, RoundedCornerShape(12.dp))
                                                .clickable { selectedColor = colorVal })
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        suggestedColors.drop(4).forEach { colorVal ->
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1.3f).clip(RoundedCornerShape(12.dp)).background(Color(colorVal))
                                                .border(if (selectedColor == colorVal) 3.dp else 1.dp, if (selectedColor == colorVal) Color(0xFF4A5D91) else Color.LightGray, RoundedCornerShape(12.dp))
                                                .clickable { selectedColor = colorVal })
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                CustomColorPicker(selectedColor) { selectedColor = it }
                            }
                        }
                        1 -> { // VOIX
                            Column {
                                Text("Ajustement de la synthèse :", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Voix d'enfant", modifier = Modifier.width(100.dp), fontSize = 12.sp)
                                    Slider(value = tempPitch, onValueChange = { tempPitch = it }, valueRange = 0.5f..2.0f, modifier = Modifier.weight(1f))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Vitesse", modifier = Modifier.width(100.dp), fontSize = 12.sp)
                                    Slider(value = tempRate, onValueChange = { tempRate = it }, valueRange = 0.3f..1.5f, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        2 -> { // TACTILE
                            Column {
                                Text("Ajustements moteurs :", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Anti-tremblement", modifier = Modifier.width(100.dp), fontSize = 12.sp)
                                    Slider(value = tempDebounce, onValueChange = { tempDebounce = it }, valueRange = 0f..2f, modifier = Modifier.weight(1f))
                                    Text(String.format(Locale.US, "%.1fs", tempDebounce), modifier = Modifier.width(40.dp), textAlign = TextAlign.End, fontSize = 12.sp)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Retour à l'accueil", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Après le choix d'un mot dans un dossier", fontSize = 12.sp, color = Color.Gray)
                                    }
                                    Switch(checked = tempAutoReturn, onCheckedChange = { tempAutoReturn = it })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveSettings(selectedColor, tempPitch, tempRate, (tempDebounce * 1000).toLong(), tempCviMode, tempAutoReturn)
                    onDismiss()
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD1D5E3))
            ) { Text("Enregistrer", color = Color(0xFF4A5D91)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = Color(0xFF4A5D91)) }
        }
    )
}

/**
 * Sélecteur de couleur personnalisé (HSV) pour une précision totale.
 */
@Composable
fun CustomColorPicker(currentColor: Long, imageUrl: String? = null, onColorChange: (Long) -> Unit) {
    val hsv = remember(currentColor) {
        FloatArray(3).apply { android.graphics.Color.colorToHSV(currentColor.toInt(), this) }
    }
    var hue by remember(currentColor) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(currentColor) { mutableFloatStateOf(hsv[1]) }
    var value by remember(currentColor) { mutableFloatStateOf(hsv[2]) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Grand rectangle d'aperçu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(currentColor))
                .border(2.dp, Color.LightGray, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            val isDark = value < 0.6f && (saturation > 0.4f || value < 0.3f)
            
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    "Aperçu",
                    color = if (isDark) Color.White else Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Teinte
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Teinte", modifier = Modifier.width(80.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                        )
                    )
            ) {
                Slider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        val newColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                        onColorChange(newColor.toLong() and 0xFFFFFFFFL)
                    },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }
            Text("${hue.toInt()}°", modifier = Modifier.width(45.dp), textAlign = TextAlign.End, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Luminosité
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lumière", modifier = Modifier.width(80.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            // Dégradé du noir vers la couleur vive à 100% de saturation et valeur
            val lumiGradient = Brush.horizontalGradient(
                listOf(Color.Black, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(lumiGradient)
            ) {
                Slider(
                    value = value,
                    onValueChange = {
                        value = it
                        val newColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                        onColorChange(newColor.toLong() and 0xFFFFFFFFL)
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }
            Text("${(value * 100).toInt()}%", modifier = Modifier.width(45.dp), textAlign = TextAlign.End, fontSize = 12.sp)
        }
    }
}

/**
 * Jeu de calibration tactile (attraper la coccinelle).
 * Permet de mesurer l'imprécision tactile de l'utilisateur pour appliquer un offset compensatoire.
 */
@Composable
fun CalibrationTest(onFinished: (Float, Float) -> Unit) {
    val targets = remember { listOf(Offset(0.5f, 0.5f), Offset(0.2f, 0.2f), Offset(0.8f, 0.2f), Offset(0.2f, 0.8f), Offset(0.8f, 0.8f)) }
    var currentStep by remember { mutableIntStateOf(0) }
    val errorsX = remember { mutableStateListOf<Float>() }
    val errorsY = remember { mutableStateListOf<Float>() }
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize().background(Color.White).pointerInput(Unit) {
        detectTapGestures { offset ->
            if (currentStep < targets.size) {
                val target = targets[currentStep]
                val targetPxX = target.x * size.width
                val targetPxY = target.y * size.height

                val errX = offset.x - targetPxX
                val errY = offset.y - targetPxY

                errorsX.add(errX)
                errorsY.add(errY)

                currentStep++
                if (currentStep >= targets.size) {
                    val avgX = errorsX.average().toFloat()
                    val avgY = errorsY.average().toFloat()
                    // On convertit les pixels en dp pour l'offset
                    with(density) {
                        onFinished(avgX.toDp().value, avgY.toDp().value)
                    }
                }
            }
        }
    }) {
        Text("Attrape la coccinelle", modifier = Modifier.align(Alignment.TopCenter).padding(32.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        // Affichage de la cible à une position relative
        Box(modifier = Modifier.fillMaxSize()) {
            if (currentStep < targets.size) {
                val target = targets[currentStep]
                Text("🐞", fontSize = 80.sp, modifier = Modifier.align(BiasAlignment(target.x * 2 - 1, target.y * 2 - 1)))
            }
        }
    }
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

/** Blocs de contenu d'une rubrique d'aide. */
private sealed interface HelpBlock
private data class HelpText(val text: String) : HelpBlock
private data class HelpSteps(val title: String?, val steps: List<String>) : HelpBlock
/** Tableau à deux colonnes : exemple -> résultat, ou option -> explication. */
private data class HelpRows(val title: String?, val rows: List<Pair<String, String>>, val arrow: Boolean = false) : HelpBlock
private data class HelpTip(val text: String, val isNew: Boolean = false) : HelpBlock
private data class HelpColors(val colors: List<Pair<Long, String>>) : HelpBlock
private data object HelpScreenMap : HelpBlock

private data class HelpTopic(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val isNew: Boolean = false,
    val blocks: List<HelpBlock>
)

private val helpTopics = listOf(
    HelpTopic(
        Icons.Default.Home, "Bien démarrer", "Les zones de l'écran",
        blocks = listOf(
            HelpText("Nina permet de parler en touchant des images (pictogrammes). Chaque image touchée est dite à voix haute."),
            HelpScreenMap,
            HelpSteps(
                "Les zones de l'écran", listOf(
                    "Barre de phrase (en haut) : les mots choisis s'y alignent. Le bouton vert ▶, ou un appui sur la barre, lit la phrase.",
                    "Dossiers (colonne de gauche) : 🏠 Accueil toujours en haut (il dit « Accueil » et revient à la première page), puis les catégories (Actions, Nourriture…). Un toucher ouvre le dossier depuis n'importe où ; le dossier ouvert est encadré. S'il y a trop de dossiers, ▲ ▼ changent de page.",
                    "Grille (au centre) : à l'accueil, les mots essentiels (moi, vouloir, encore, pas…) toujours à la même place ; dans un dossier, ses mots. Elle ne défile jamais : s'il y a trop de pictos, ◀ ▶ en bas changent de page et disent « Page suivante » ou « Page précédente ». Le bouton ← revient en arrière dans les sous-dossiers et dit « Retour ».",
                    "Phrases rapides (colonne de droite, éclair ⚡) : oui, non, bonjour, merci, toilettes… dites tout de suite, sans passer par la phrase.",
                    "Cadenas (petit, gris, en haut à droite) : à maintenir 2 secondes pour ouvrir le mode adulte."
                )
            ),
            HelpTip("À droite de la barre de phrase, la loupe 🔍 cherche un mot et peut montrer où il se trouve ; le clavier ⌨ permet d'écrire un mot qui n'a pas de picto."),
            HelpTip(
                "Bien organiser : un mot = une seule place (l'enfant mémorise le geste). Gardez 6 à 8 grands dossiers et rangez le reste en sous-dossiers. " +
                "Mode adulte › ⋮ › « Grilles prêtes » installe une grille complète (3 niveaux, de 12 à 30 mots à l'accueil), à adapter ensuite avec l'orthophoniste.",
                isNew = true
            )
        )
    ),
    HelpTopic(
        Icons.Default.GridView, "Grilles prêtes", "Installer un vocabulaire complet", isNew = true,
        blocks = listOf(
            HelpText("Plutôt que de créer chaque picto à la main, installez une grille déjà remplie : mots essentiels à l'accueil, dossiers thématiques à gauche et phrases rapides à droite. Elle sert de point de départ, que vous adaptez ensuite."),
            HelpSteps(
                "Installer une grille", listOf(
                    "Ouvrez le mode adulte (maintenir le cadenas 2 secondes, puis le calcul).",
                    "Dans la barre jaune, touchez ⋮ puis « Grilles prêtes… ».",
                    "Choisissez un niveau à gauche : l'aperçu à droite montre l'accueil, chaque dossier et les phrases rapides.",
                    "Touchez « Installer ». La grille, les dossiers, les phrases rapides et la taille de grille sont réglés d'un coup.",
                    "Les images se téléchargent dans la tablette (il faut du réseau une fois), puis tout fonctionne hors ligne."
                )
            ),
            HelpRows(
                "Les trois niveaux", listOf(
                    "Premiers mots" to "débutant — 51 mots, 12 à l'accueil, 5 petits dossiers, 4 pictos par rangée",
                    "Vocabulaire courant" to "intermédiaire — 95 mots, 20 à l'accueil, 6 dossiers dont Jeux, 5 par rangée ; ajoute J'ai mal et Stop",
                    "Vocabulaire étendu" to "avancé — 149 mots, 30 à l'accueil avec les questions (quoi, où, qui, quand, pourquoi), sous-dossiers, 6 par rangée"
                )
            ),
            HelpText("À l'accueil se trouvent les mots les plus employés par les enfants (moi, vouloir, encore, fini, pas…), rangés par couleur dans l'ordre de la phrase. D'un niveau à l'autre, chaque mot garde exactement sa place : les nouveaux mots s'ajoutent à droite et en bas. L'enfant peut donc passer au niveau suivant sans réapprendre ses gestes."),
            HelpTip("Installer une grille remplace tous les pictos actuels. Votre disposition est gardée : ⋮ › « Revenir à la disposition précédente » la remet en place. Pour garder une copie durable, faites aussi ⋮ › « Partager / Sauvegarder » avant."),
            HelpTip("Ces listes sont un point de départ, pas un vocabulaire validé pour un enfant précis. Montrez-les à l'orthophoniste, puis adaptez-les : glisser-déposer, mode Édition, ajout de photos de la famille et des objets de la maison.")
        )
    ),
    HelpTopic(
        Icons.Default.RecordVoiceOver, "Construire une phrase", "Barre de phrase et grammaire",
        blocks = listOf(
            HelpSteps(
                null, listOf(
                    "Touche les pictos dans l'ordre : chaque mot est dit et s'ajoute à la barre, avec son image et son texte.",
                    "Pour corriger, ⌫ efface le dernier mot. La poubelle rouge efface toute la phrase.",
                    "Appuie sur ▶ ou touche la barre : Nina lit la phrase en la mettant en bon français."
                )
            ),
            HelpRows(
                "Ce que Nina corrige", listOf(
                    "Moi vouloir manger" to "Je veux manger",
                    "Maman aller maison" to "Maman va maison",
                    "Nous manger gâteau" to "Nous mangeons gâteau",
                    "Moi pas aimer" to "Je n'aime pas",
                    "Moi se laver" to "Je me lave",
                    "Moi boire de le eau" to "Je bois de l'eau",
                    "Jouer avec moi" to "Jouer avec moi (inchangé)"
                ), arrow = true
            ),
            HelpText("Seul le premier verbe après le sujet est conjugué ; les suivants restent à l'infinitif (« je veux manger »). Nina n'ajoute pas les petits mots (le, à la…) : il faut les choisir."),
            HelpTip("Après un mot choisi dans un dossier, la grille revient seule à l'accueil. Réglable dans Paramètres › Tactile.", isNew = true)
        )
    ),
    HelpTopic(
        Icons.Default.TextFields, "Formes du mot", "Conjuguer, mettre au pluriel", isNew = true,
        blocks = listOf(
            HelpText("Cadenas fermé, un appui long sur un picto ouvre ses différentes formes. Toucher une forme l'ajoute à la phrase et la fait dire."),
            HelpRows(
                "Exemples", listOf(
                    "Manger" to "manger, mange, manges, mangeons, mangez, mangent, mangé",
                    "Aller" to "aller, vais, vas, va, allons, allez, vont, allé",
                    "Chat" to "chat, chats",
                    "Cheval" to "cheval, chevaux"
                ), arrow = true
            ),
            HelpText("Sous chaque forme, une indication dit pour qui elle sert (je, tu, nous… ou « passé »). Pratique pour apprendre à conjuguer : « nous » puis « mangeons »."),
            HelpTip("Rien ne s'ouvre pour les dossiers, les phrases rapides et les petits mots (encore, oui, avec…). Cadenas ouvert, l'appui long sert à déplacer les pictos.")
        )
    ),
    HelpTopic(
        Icons.Default.TouchApp, "Montrer un mot", "Modélisation pas à pas", isNew = true,
        blocks = listOf(
            HelpText("Pour apprendre où se trouve un mot, Nina montre le chemin case par case, comme un adulte qui pointe du doigt."),
            HelpSteps(
                null, listOf(
                    "Touche la loupe 🔍 et tape le début du mot.",
                    "Touche le résultat ou le bouton « Montrer ».",
                    "La case à toucher clignote en orange. Un bandeau indique l'étape (1/2, 2/2…).",
                    "À chaque bonne case touchée, la suivante s'allume. Le guidage s'arrête sur le mot."
                )
            ),
            HelpText("Conseil : montrez le chemin vous-même pendant que l'enfant regarde, puis laissez-le refaire. C'est la « modélisation », recommandée en CAA."),
            HelpTip("« Arrêter » dans le bandeau annule le guidage. Les pictos masqués ne sont pas proposés.")
        )
    ),
    HelpTopic(
        Icons.Default.Palette, "Code couleur", "Repérer le type de mot",
        blocks = listOf(
            HelpText("Les couleurs aident à repérer le type de mot (code Fitzgerald). La couleur se choisit dans la fenêtre « Modifier le picto »."),
            HelpColors(
                listOf(
                    0xFFFFD54FL to "Personnes : sujets, pronoms (moi, maman)",
                    0xFF81C784L to "Actions : verbes (vouloir, jouer)",
                    0xFFFFB74DL to "Choses : noms, objets, aliments",
                    0xFF64B5F6L to "Descriptifs : adjectifs, émotions",
                    0xFFF06292L to "Social : politesse, salutations",
                    0xFFBA68C8L to "Questions : mots interrogatifs",
                    0xFFE0E0E0L to "Petits mots : prépositions, liens"
                )
            )
        )
    ),
    HelpTopic(
        Icons.Default.Lock, "Mode adulte", "Cadenas, barre d'outils, déplacer",
        blocks = listOf(
            HelpSteps(
                "Ouvrir le mode adulte", listOf(
                    "Maintiens le petit cadenas gris (en haut à droite) pendant 2 secondes : un anneau se remplit. Un simple appui ne fait rien, pour que l'enfant ne l'ouvre pas par hasard.",
                    "Résous le petit calcul (par exemple 3 + 4).",
                    "Une barre jaune « Mode adulte » apparaît en haut de l'écran, avec tous les outils."
                )
            ),
            HelpRows(
                "La barre du mode adulte", listOf(
                    "Ajouter" to "créer un picto : dans la grille, la colonne des dossiers ou les phrases rapides",
                    "Édition" to "modifier les pictos (un crayon apparaît) ; le bouton reste foncé tant que l'édition est active",
                    "Paramètres" to "couleurs, voix, réglages tactiles",
                    "Statistiques" to "suivi de l'utilisation",
                    "Aide" to "cette aide",
                    "− 4 par rangée +" to "taille de la grille, de 1 à 8 pictos par rangée",
                    "⋮" to "calibrer le tactile, mode Kiosque, partager / restaurer la sauvegarde, grilles prêtes",
                    "Verrouiller" to "referme le mode adulte (l'édition s'arrête aussi)"
                )
            ),
            HelpSteps(
                "Déplacer un picto (glisser-déposer)", listOf(
                    "Cadenas ouvert, fais un appui long sur un picto puis glisse-le.",
                    "Lâché au centre d'une case : les deux pictos s'échangent.",
                    "Lâché sur le bord gauche ou droit d'une case : il s'insère avant ou après.",
                    "Lâché sur une colonne : il y est copié (mots essentiels ou phrases rapides).",
                    "Pour l'emmener sur une autre page, garde-le une seconde sur le bouton ◀ ou ▶ : la page tourne."
                )
            ),
            HelpTip("Referme le cadenas avant de rendre la tablette : le mode Édition se coupe automatiquement.")
        )
    ),
    HelpTopic(
        Icons.Default.Edit, "Personnaliser un picto", "Ajouter, modifier, voix, image",
        blocks = listOf(
            HelpSteps(
                "Ajouter un picto", listOf(
                    "Barre du mode adulte › Ajouter, puis choisis la zone : grille (ou dossier ouvert), colonne des dossiers ou phrases rapides.",
                    "Choisis un texte, une image (recherche dans les banques ARASAAC, Mulberry, Sclera… ou photo de la galerie) et une couleur.",
                    "Touche « Créer »."
                )
            ),
            HelpSteps(
                "Modifier un picto", listOf(
                    "Barre du mode adulte › Édition : un crayon apparaît sur chaque picto.",
                    "Touche le picto à modifier.",
                    "Onglets : Apparence, Audio, Dossier, Suppression.",
                    "Touche « Enregistrer les modifications ». Touche à nouveau « Édition » pour finir."
                )
            ),
            HelpRows(
                "Les options", listOf(
                    "Texte du picto" to "ce qui s'affiche et s'ajoute à la phrase",
                    "Texte prononcé" to "ce qui est dit quand on le touche (Toilettes → « J'ai besoin d'aller aux toilettes »). Nouveau",
                    "Audio" to "enregistrer sa propre voix, jouée à la place de la synthèse",
                    "Dossier" to "transformer en dossier, ou déplacer le picto dans un dossier",
                    "Phrase rapide" to "dite tout de suite, sans aller dans la phrase",
                    "Masquer" to "caché pour l'enfant ; sa place reste vide pour ne pas déplacer les autres"
                )
            )
        )
    ),
    HelpTopic(
        Icons.Default.BarChart, "Statistiques", "Suivi et export CSV", isNew = true,
        blocks = listOf(
            HelpText("Chaque mot touché et chaque phrase lue avec ▶ sont notés avec la date et l'heure. Ces données restent sur la tablette."),
            HelpRows(
                null, listOf(
                    "Période" to "aujourd'hui, 7 jours, 30 jours ou tout",
                    "Mots" to "les mots les plus utilisés",
                    "Par jour" to "le nombre de mots chaque jour",
                    "Phrases" to "l'historique daté des phrases prononcées",
                    "Exporter (CSV)" to "un fichier pour Excel, à envoyer par mail ou Drive (par exemple à l'orthophoniste)"
                )
            ),
            HelpTip("« Remettre à zéro » demande une confirmation, puis efface tout l'historique.")
        )
    ),
    HelpTopic(
        Icons.Default.Visibility, "Commande oculaire", "Choisir les pictos du regard", isNew = true,
        blocks = listOf(
            HelpText("La caméra frontale suit le regard : l'enfant choisit un picto en le fixant, sans toucher l'écran. Tout se passe dans la tablette, aucune image n'est enregistrée."),
            HelpSteps(
                "Mise en place", listOf(
                    "Posez la tablette sur un support stable, face à l'enfant, à 40–60 cm, visage bien éclairé de face.",
                    "Réglez la taille de grille que l'enfant utilisera (3 ou 4 pictos par rangée conseillés).",
                    "Mode adulte › ⋮ › Commande oculaire. Autorisez la caméra la première fois.",
                    "« Calibrer sur les pictos » : d'abord, les yeux restent sur l'étoile au centre pendant que la tête tourne doucement à gauche, à droite, en haut, en bas (quelques secondes). Puis l'enfant suit des yeux l'étoile, qui se pose sur chaque picto.",
                    "« Essai sur les pictos » : vérifiez qu'en fixant une case, l'anneau se remplit sur la bonne.",
                    "Activez « Sélection par le regard », puis refermez le cadenas."
                )
            ),
            HelpRows(
                "Ce qui rend la sélection fiable", listOf(
                    "Calibration sur les pictos" to "les mesures sont prises à l'emplacement exact de chaque picto, et l'erreur restante y est corrigée",
                    "Aimant" to "le regard est rattaché à la case la plus proche, et n'en change qu'après une hésitation franche",
                    "Fixation" to "l'anneau orange se remplit (1,2 s par défaut, réglable) : un coup d'œil ou un clignement ne déclenche rien",
                    "Repos" to "après une sélection, la même case ne se redéclenche qu'après un court repos",
                    "Rotation de la tête" to "mesurée au début de la calibration, puis compensée : tourner un peu la tête ne fait plus dériver le pointeur",
                    "Calibration continue" to "chaque picto touché, ou choisi du regard sans être effacé aussitôt, devient un point de calibration : la calibration s'adapte à l'enfant au fil de l'utilisation, zone par zone",
                    "Petits boutons" to "dossiers, phrases rapides, Lire, ⌫ attirent le regard dans un rayon d'au moins 1,3 cm",
                    "Anneau tolérant" to "un tremblement ou un passage entre deux cases met l'anneau en pause au lieu de le vider"
                )
            ),
            HelpRows(
                "Si le pointeur manque de précision", listOf(
                    "Grille adaptée au regard" to "moins de pictos par rangée (4 conseillés), des dossiers et phrases rapides plus hauts, et de grands espaces entre les cases, quand la sélection par le regard est active",
                    "Zoom sur les petites cases" to "à mi-chemin de la fixation d'une petite case, la zone s'agrandit deux fois ; on termine la fixation sur la case agrandie (activé par défaut)",
                    "Sélection en deux temps" to "fixer une zone l'agrandit deux fois (anneau bleu), puis fixer le picto agrandi l'active (anneau orange). Sans choix pendant 4 s, l'écran revient à la normale"
                )
            ),
            HelpRows(
                "Faire évoluer la calibration", listOf(
                    "Retouche rapide" to "6 pictos remesurés et ajoutés à la calibration existante : après un changement de lumière, de position ou de support",
                    "Calibrer sur les pictos" to "tout refaire, par exemple après un changement de taille de grille",
                    "Vérifier la précision" to "mesure l'écart sur 5 pictos, en cm"
                )
            ),
            HelpTip("La sélection par le regard est coupée en mode adulte et quand une fenêtre est ouverte. Les phrases rapides, Accueil, Play, ⌫, ← et les boutons de page se choisissent aussi du regard.")
        )
    ),
    HelpTopic(
        Icons.Default.Accessibility, "Réglages et accessibilité", "Vue, voix, toucher",
        blocks = listOf(
            HelpRows(
                "Dans Paramètres", listOf(
                    "Contraste élevé (CVI)" to "fond noir et bordures jaunes, pour la déficience visuelle",
                    "Couleur de fond" to "couleurs proposées ou personnalisées",
                    "Voix" to "hauteur (voix d'enfant) et vitesse",
                    "Anti-tremblement" to "ignore les appuis répétés trop rapprochés",
                    "Retour à l'accueil" to "revenir à l'accueil après un mot pris dans un dossier. Nouveau"
                )
            ),
            HelpRows(
                "Dans la barre du mode adulte", listOf(
                    "Taille de grille" to "boutons − et + de la barre du mode adulte : de 1 à 8 pictos par rangée. La grille remplit l'écran sans défiler ; s'il y a trop de pictos, des pages ◀ ▶ apparaissent. Nouveau",
                    "⋮ › Calibrer le tactile" to "attraper 5 fois la coccinelle : l'écran se décale pour compenser",
                    "⋮ › Mode Kiosque" to "empêche de quitter Nina par erreur"
                )
            ),
            HelpTip("Une fois la grille installée, évitez de changer le nombre de colonnes : l'enfant retient l'emplacement des mots."),
            HelpTip(
                "Voix sans internet : si la voix française de la tablette a besoin d'internet, Nina serait muette sans Wi-Fi. " +
                "Un point orange apparaît alors sur le cadenas, et un bouton orange dans la barre du mode adulte explique comment installer la voix (une seule fois).",
                isNew = true
            ),
            HelpTip(
                "Plein écran : Nina cache l'heure, la batterie et les boutons Android (‹ ○ |||) pour laisser toute la place aux pictos et éviter les appuis par erreur. " +
                "Pour les faire réapparaître quelques secondes, glissez le doigt depuis le bord haut ou bas de l'écran ; ils se recachent seuls.",
                isNew = true
            )
        )
    ),
    HelpTopic(
        Icons.Default.Save, "Sauvegarde", "Changer de tablette",
        blocks = listOf(
            HelpRows(
                null, listOf(
                    "⋮ › Partager / Sauvegarder" to "crée un fichier avec la grille et les deux colonnes, à envoyer (Drive, mail…)",
                    "⋮ › Restaurer" to "recharge un fichier de sauvegarde, y compris les anciennes",
                    "⋮ › Sauvegardes automatiques" to "revenir à la grille d'un des 7 derniers jours, en cas de mauvaise manipulation. Nouveau"
                )
            ),
            HelpTip(
                "Sauvegarde automatique : chaque jour d'utilisation, Nina garde une copie de la grille dans la tablette (7 jours). " +
                "Si la grille enregistrée est abîmée, Nina la récupère toute seule au démarrage et vous prévient. " +
                "Ces copies restent dans la tablette : pour changer de tablette, utilisez toujours « Partager / Sauvegarder ».",
                isNew = true
            ),
            HelpTip("Les images des banques (ARASAAC, Mulberry…) sont enregistrées dans la tablette et fonctionnent hors réseau. Sur une nouvelle tablette, elles sont retéléchargées automatiquement après la restauration (il faut du réseau une fois)."),
            HelpTip("Les photos de la galerie et les voix enregistrées restent sur la tablette : sur une nouvelle tablette, elles seront à refaire. Les statistiques ne sont pas dans la sauvegarde.")
        )
    ),
    HelpTopic(
        Icons.Default.Image, "Images et crédits", "Banques de pictogrammes", isNew = true,
        blocks = listOf(
            HelpText("En créant ou modifiant un picto, « Chercher » interroge plusieurs banques à la fois. Les filtres permettent de n'en voir qu'une. L'image choisie est enregistrée dans la tablette : elle reste visible sans réseau."),
            HelpRows(
                "Les banques et leurs licences", listOf(
                    "ARASAAC" to "Sergio Palao, Gouvernement d'Aragon (arasaac.org) — CC BY-NC-SA",
                    "Mulberry" to "Steve Lee, mulberrysymbols.org — CC BY-SA",
                    "Sclera" to "Sclera vzw, sclera.be — CC BY-NC",
                    "Blissymbolics" to "Blissymbolics Communication International — CC BY-SA",
                    "Tawasol, Twemoji, Noun Project…" to "via opensymbols.org — licence indiquée sous l'image choisie"
                )
            ),
            HelpText("Recherche fournie par globalsymbols.com et opensymbols.org. « NC » signifie usage non commercial : parfait pour un usage personnel ou thérapeutique, mais pas pour vendre l'application."),
            HelpTip("Si une banque ne répond pas, les autres s'affichent quand même. Sans réseau, la recherche est impossible, mais tous les pictos déjà créés restent visibles.")
        )
    )
)

private val HelpAccent = Color(0xFF4A5D91)
private val HelpNew = Color(0xFFFF6D00)

/**
 * Aide & Tutoriel en plein écran : rubriques à gauche, contenu à droite,
 * boutons Précédent / Suivant pour la lire comme un tutoriel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    val topic = helpTopics[selected]

    // decorFitsSystemWindows = false : la fenêtre couvre tout l'écran (sinon une bande de l'appli reste visible en bas)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        HideSystemBarsInDialog()
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF0F0F5)) {
            Column(modifier = Modifier.systemBarsPadding()) {
                TopAppBar(
                    title = { Text("Aide & Tutoriel", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Fermer") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    // Sommaire
                    LazyColumn(
                        modifier = Modifier.width(300.dp).fillMaxHeight().background(Color.White),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(helpTopics.size) { index ->
                            HelpNavItem(helpTopics[index], selected = index == selected) { selected = index }
                        }
                    }

                    // Contenu de la rubrique (défilement remis en haut à chaque changement)
                    key(selected) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(topic.icon, contentDescription = null, tint = HelpAccent, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(topic.title, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color(0xFF1F2A4D))
                                        if (topic.isNew) { Spacer(modifier = Modifier.width(12.dp)); NewBadge() }
                                    }
                                    Text(topic.subtitle, fontSize = 16.sp, color = Color.Gray)
                                }
                                Text("${selected + 1} / ${helpTopics.size}", color = Color.Gray)
                            }

                            topic.blocks.forEach { HelpBlockView(it) }

                            // Navigation façon tutoriel
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                if (selected > 0) {
                                    OutlinedButton(onClick = { selected-- }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(helpTopics[selected - 1].title)
                                    }
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                if (selected < helpTopics.lastIndex) {
                                    Button(onClick = { selected++ }, colors = ButtonDefaults.buttonColors(containerColor = HelpAccent)) {
                                        Text(helpTopics[selected + 1].title)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                } else {
                                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = HelpAccent)) { Text("Terminer") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpNavItem(topic: HelpTopic, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFE3E7F5) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(topic.icon, contentDescription = null, tint = if (selected) HelpAccent else Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(topic.title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) HelpAccent else Color.DarkGray)
            Text(topic.subtitle, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
        }
        if (topic.isNew) Box(modifier = Modifier.size(8.dp).background(HelpNew, CircleShape))
    }
}

@Composable
private fun NewBadge() {
    Text(
        "NOUVEAU", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White,
        modifier = Modifier.background(HelpNew, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun HelpBlockView(block: HelpBlock) {
    when (block) {
        is HelpText -> Text(block.text, fontSize = 17.sp, lineHeight = 26.sp, color = Color(0xFF333333))

        is HelpSteps -> HelpCard(block.title) {
            block.steps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp).background(HelpAccent, CircleShape)) {
                        Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(step, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        is HelpRows -> HelpCard(block.title) {
            block.rows.forEachIndexed { i, (left, right) ->
                if (i > 0) HorizontalDivider(color = Color(0xFFE8E8EE))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(left, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(230.dp))
                    if (block.arrow) Text("→", color = HelpAccent, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    val isNew = right.endsWith(". Nouveau")
                    Text(right.removeSuffix(". Nouveau"), fontSize = 16.sp, color = Color(0xFF444444), modifier = Modifier.weight(1f))
                    if (isNew) { Spacer(modifier = Modifier.width(8.dp)); NewBadge() }
                }
            }
        }

        is HelpTip -> Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
                .background(if (block.isNew) Color(0xFFFFF3E0) else Color(0xFFE8F5E9), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(if (block.isNew) "✨" else "💡", fontSize = 20.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(block.text, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f))
            if (block.isNew) { Spacer(modifier = Modifier.width(8.dp)); NewBadge() }
        }

        is HelpColors -> HelpCard(null) {
            block.colors.forEach { (color, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(modifier = Modifier.size(32.dp).background(Color(color), RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(label, fontSize = 16.sp)
                }
            }
        }

        HelpScreenMap -> ScreenMap()
    }
}

@Composable
internal fun HelpCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            title?.let { Text(it, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = HelpAccent, modifier = Modifier.padding(bottom = 8.dp)) }
            content()
        }
    }
}

/** Plan simplifié de l'écran de Nina, avec le nom de chaque zone. */
@Composable
private fun ScreenMap() {
    @Composable
    fun Zone(label: String, color: Long, modifier: Modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(Color(color), RoundedCornerShape(10.dp)).padding(6.dp)
        ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color(0xFF333333)) }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF3E0)), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(230.dp).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.height(50.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Zone("▶", 0xFFA5D6A7, Modifier.width(50.dp).fillMaxHeight())
                    Zone("Barre de phrase", 0xFFFFFFFF, Modifier.weight(1f).fillMaxHeight())
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Zone("🏠 Accueil\n+ dossiers", 0xFFE0E0E0, Modifier.width(90.dp).fillMaxHeight())
                    Zone("Grille : mots essentiels", 0xFFC8E6C9, Modifier.weight(1f).fillMaxHeight())
                }
            }
            Column(modifier = Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Zone("🔒 maintenir", 0xFFE0E0E0, Modifier.fillMaxWidth().height(50.dp))
                Zone("⚡ Phrases\nrapides", 0xFFBBDEFB, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

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
