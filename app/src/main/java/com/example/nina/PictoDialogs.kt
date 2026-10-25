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

/* Création et modification d'un picto. */

enum class EditTab { APPARENCE, AUDIO, DOSSIER, DANGER }
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
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Image du picto", fontWeight = FontWeight.Bold)
                                Text("Une photo (la tasse de Nina, maman…), une image de la galerie, ou un picto des banques d'images.", style = MaterialTheme.typography.bodySmall)
                                PhotoSourceButtons(onImage = { selectedUrl = it })
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
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Image du picto", fontWeight = FontWeight.Bold)
                                        Text("Une photo, une image de la galerie, ou un picto des banques d'images.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        PhotoSourceButtons(onImage = { imageUrl = it })
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
