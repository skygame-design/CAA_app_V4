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
    var isFolder by remember { mutableStateOf(value = false) }
    var isQuickPhrase by remember { mutableStateOf(value = false) }
    var isHidden by remember { mutableStateOf(value = false) }
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    var selectedColor by remember { mutableLongStateOf(0xFFFFD54FL) }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(value = false) }

    var audioPath by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    val recorder = remember { AudioRecorder(context) }
    val scope = rememberCoroutineScope()
    val searchResults = remember { mutableStateListOf<OpenSymbolResponse>() }

    // Lanceur pour choisir une image dans la galerie
    val photoLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                selectedUrl = saveImageToInternalStorage(context, it)
            }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
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
                                    val finalUrl = if (selectedUrl?.startsWith("http") == true) saveImageLocally(context, selectedUrl!!, "${label.trim()}_${System.currentTimeMillis()}") else selectedUrl
                                    withContext(Dispatchers.Main) {
                                        isSaving = false
                                        onConfirm(AACCell(label = label, colorLong = selectedColor, imageUrl = finalUrl, audioPath = audioPath, isFolder = isFolder, isQuickPhrase = isQuickPhrase, isHidden = isHidden))
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
                                Text("Choisissez une image ou cherchez sur ARASAAC.", style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = {
                                        if (label.isBlank()) return@Button
                                        scope.launch(Dispatchers.IO) {
                                            withContext(Dispatchers.Main) { isLoading = true; searchResults.clear() }
                                            try {
                                                val araResults = RetrofitInstance.arasaacApi.searchPictograms(label.trim().lowercase())
                                                val mapped = araResults.map { OpenSymbolResponse("ARASAAC", "https://static.arasaac.org/pictograms/${it._id}/${it._id}_300.png") }
                                                withContext(Dispatchers.Main) { searchResults.addAll(mapped) }
                                            } catch (e: Exception) { e.printStackTrace() }
                                            finally { withContext(Dispatchers.Main) { isLoading = false } }
                                        }
                                    },
                                    modifier = Modifier.padding(top = 8.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                    else Text("Chercher ARASAAC", fontSize = 12.sp)
                                }
                            }
                        }

                        if (searchResults.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(searchResults) { res ->
                                    AsyncImage(model = res.imageUrl, contentDescription = null, modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)).border(if (selectedUrl == res.imageUrl) 3.dp else 1.dp, if (selectedUrl == res.imageUrl) MaterialTheme.colorScheme.primary else Color.LightGray, RoundedCornerShape(8.dp)).clickable { selectedUrl = res.imageUrl })
                                }
                            }
                        }
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
    // Les deux derniers paramètres : déplacement demandé, et dossier cible (null = Racine)
    onSave: (AACCell, String, Long, String?, String?, Boolean, Boolean, Boolean, Boolean, AACCell?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(cell.label) }
    var imageUrl by remember { mutableStateOf(cell.imageUrl) }
    var color by remember { mutableLongStateOf(cell.colorLong) }
    var isFolder by remember { mutableStateOf(cell.isFolder) }
    var isQuickPhrase by remember { mutableStateOf(cell.isQuickPhrase) }
    var isHidden by remember { mutableStateOf(cell.isHidden) }
    var isLoading by remember { mutableStateOf(false) }
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

    val searchResults = remember { mutableStateListOf<OpenSymbolResponse>() }
    val scope = rememberCoroutineScope()
    val availableFolders = allCells.filter { it.isFolder && it != cell }

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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
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
                                        Button(
                                            onClick = {
                                                if (name.isBlank()) return@Button
                                                scope.launch(Dispatchers.IO) {
                                                    withContext(Dispatchers.Main) { isLoading = true; searchResults.clear() }
                                                    try {
                                                        val results = RetrofitInstance.arasaacApi.searchPictograms(name.trim().lowercase())
                                                        val mapped = results.map { OpenSymbolResponse("ARASAAC", "https://static.arasaac.org/pictograms/${it._id}/${it._id}_300.png") }
                                                        withContext(Dispatchers.Main) { searchResults.addAll(mapped) }
                                                    } catch (e: Exception) { e.printStackTrace() }
                                                    finally { withContext(Dispatchers.Main) { isLoading = false } }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                            else Text("Chercher ARASAAC", fontSize = 12.sp)
                                        }
                                        Text("Appuyez sur le carré pour la galerie", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }

                                if (searchResults.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                        items(searchResults) { res ->
                                            AsyncImage(
                                                model = res.imageUrl, contentDescription = null,
                                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).border(if (imageUrl == res.imageUrl) 3.dp else 1.dp, if (imageUrl == res.imageUrl) Color(0xFF4A5D91) else Color.LightGray, RoundedCornerShape(8.dp)).clickable { imageUrl = res.imageUrl }
                                            )
                                        }
                                    }
                                }

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
                                onSave(cell, name, color, imageUrl, audioPath, isFolder, isQuickPhrase, isHidden, isMoveRequested, pendingFolderMove)
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
    onDismiss: () -> Unit,
    onSaveSettings: (Long, Float, Float, Long, Boolean) -> Unit
) {
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
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveSettings(selectedColor, tempPitch, tempRate, (tempDebounce * 1000).toLong(), tempCviMode)
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
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults = remember(searchQuery, allButtons) {
        findWordPaths(searchQuery, allButtons)
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
                            items(searchResults) { (cell, path) ->
                                Card(
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
                                        Column(modifier = Modifier.padding(start = 12.dp)) {
                                            Text(cell.label.uppercase(), fontWeight = FontWeight.Bold)
                                            Text(path, fontSize = 12.sp, color = Color.Gray)
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

/**
 * Dialogue d'aide et tutoriel.
 * Explique le fonctionnement de l'application (Grammaire, Couleurs, Accessibilité, etc.).
 */
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF0F0F5),
        title = {
            Text("Aide & Tutoriel", fontWeight = FontWeight.Bold, fontSize = 24.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                HelpSection(
                    title = "✨ Communication & Grammaire",
                    content = "Appuyez sur les pictogrammes pour construire une phrase dans la barre du haut. Le moteur transforme automatiquement le langage 'télégraphique' en français correct (ex: 'Moi + Manger + Pomme' devient 'Je mange une pomme'). L'application gère seule les apostrophes (J', L') et les articles."
                )

                HelpSection(
                    title = "🎨 Code Couleur (Fitzgerald)",
                    content = "🟡 Jaune : Personnes et Sujets (Moi, Famille)\n" +
                            "🟢 Vert : Actions et Verbes (Vouloir, Jouer)\n" +
                            "🟠 Orange : Objets, Aliments et Choses\n" +
                            "🔵 Bleu : Social et Questions (Bonjour, Merci)\n" +
                            "🟣 Violet : Lieux, Temps et Prépositions (Maison, Avec, Pour)"
                )

                HelpSection(
                    title = "🛠️ Ergonomie et Accessibilité",
                    content = "• Calibration : Ajustez la sensibilité tactile pour s'adapter à la précision de l'utilisateur.\n" +
                            "• Anti-tremblement : Filtre les appuis involontaires pour ne valider que l'intention réelle.\n" +
                            "• Apparence : Changez la taille de la grille ou le contraste pour une lecture optimale."
                )

                HelpSection(
                    title = "🗣️ Voix et Synthèse",
                    content = "Réglez la vitesse (débit) et le timbre (grave/aigu) de la voix dans les paramètres pour qu'elle corresponde à la personnalité de l'enfant."
                )

                HelpSection(
                    title = "🔒 Mode Kiosque & Sécurité",
                    content = "Activez le mode Kiosque pour verrouiller l'application à l'écran. Cela empêche l'enfant de quitter accidentellement l'outil de communication."
                )

                HelpSection(
                    title = "📊 Statistiques de Communication",
                    content = "Consultez les mots les plus utilisés et la fréquence d'utilisation de l'appli pour suivre les progrès de Nina."
                )

                HelpSection(
                    title = "💾 Sauvegarde et Partage",
                    content = "• Partager/Sauvegarder : Exportez votre configuration vers le cloud.\n" +
                            "• Restaurer : Récupérez votre configuration en cas de changement de tablette."
                )

                HelpSection(
                    title = "📸 Personnalisation des Pictos",
                    content = "Faites un appui long sur un picto pour le modifier. Vous pouvez changer le texte, la couleur, ou utiliser une photo de votre galerie."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD1D5E3))
            ) {
                Text("Fermer", color = Color(0xFF4A5D91))
            }
        }
    )
}

@Composable
private fun HelpSection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4A5D91)
        )
        Text(
            text = content,
            fontSize = 14.sp,
            color = Color.Black
        )
    }
}

/**
 * Dialogue affichant les statistiques d'utilisation.
 * Montre le nombre total de mots prononcés et les mots les plus fréquents.
 */
@Composable
fun StatsDialog(
    stats: Map<String, Int>,
    onDismiss: () -> Unit,
    onReset: () -> Unit
) {
    val totalClicks = stats.values.sum()
    val sortedStats = stats.entries.sortedByDescending { it.value }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF0F0F5),
        title = { Text("📊 Statistiques d'utilisation", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total des mots prononcés", color = Color(0xFF1565C0), fontSize = 14.sp)
                        Text(totalClicks.toString(), fontWeight = FontWeight.Black, fontSize = 36.sp, color = Color(0xFF1565C0))
                    }
                }

                Text("Mots les plus utilisés :", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                if (sortedStats.isEmpty()) {
                    Text("Aucune donnée pour le moment.", color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(sortedStats) { entry ->
                            val (word, count) = entry
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(word.uppercase(), fontWeight = FontWeight.Medium)
                                Text("$count fois", color = Color.Gray, fontSize = 14.sp)
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
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("Remettre à zéro", color = Color.Red)
            }
        }
    )
}
