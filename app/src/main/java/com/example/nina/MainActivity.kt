package com.example.nina

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nina.ui.theme.NinaTheme
import com.google.gson.reflect.TypeToken
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import android.util.Log

/**
 * Activité principale de l'application Nina.
 * Gère l'initialisation du TTS, le chargement des données, et l'interface utilisateur globale.
 */
class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    // Permission pour l'enregistrement audio des pictos personnalisés
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialisation de la synthèse vocale
        tts = TextToSpeech(this, this)
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            DragAndDropRoot {
                NinaTheme {
                    val context = LocalContext.current
                    val haptic = LocalHapticFeedback.current
                    val scope = rememberCoroutineScope()
                    
                    // --- ÉTATS RÉACTIFS ---
                    
                    // Listes principales des pictogrammes
                    val boutons = remember { mutableStateListOf<AACCell>().apply { addAll(loadButtons()) } }
                    val phrase = remember { mutableStateListOf<AACCell>() }
                    val navigationStack = remember { mutableStateListOf<AACCell>() } // Pile de navigation pour les dossiers
                    val coreWords = remember { mutableStateListOf<AACCell>().apply { addAll(loadCoreWords()) } } // Barre de gauche
                    val quickFires = remember { mutableStateListOf<AACCell>().apply { addAll(loadQuickFires()) } } // Barre de droite

                    // Configuration et Dialogues
                    var columnCount by remember { mutableIntStateOf(context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getInt("column_count", 4)) }
                    var showAddDialog by remember { mutableStateOf(false) }
                    var showEditDialog by remember { mutableStateOf(false) }
                    var showKeyboardDialog by remember { mutableStateOf(false) }
                    var cellToEdit by remember { mutableStateOf<AACCell?>(null) }
                    var isAdminMode by remember { mutableStateOf(false) }
                    var showAdminChallengeDialog by remember { mutableStateOf(false) }
                    var showMenu by remember { mutableStateOf(false) }
                    var backgroundColor by remember { mutableLongStateOf(context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getLong("bg_color", 0xFFFAF3E0)) }
                    var isCviMode by remember { mutableStateOf(context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getBoolean("cvi_mode", false)) }
                    var showSettingsDialog by remember { mutableStateOf(false) }
                    var showHelpDialog by remember { mutableStateOf(false) }

                    var isEditMode by remember { mutableStateOf(false) }

                    var isAddingToCore by remember { mutableStateOf(false) }
                    var isAddingToQuickFires by remember { mutableStateOf(false) }

                    var offsetX by remember { mutableFloatStateOf(context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getFloat("offset_x", 0f)) }
                    var offsetY by remember { mutableFloatStateOf(context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getFloat("offset_y", 0f)) }
                    var showCalibration by remember { mutableStateOf(false) }
                    var showWordFinder by remember { mutableStateOf(false) }

                    var voicePitch by remember { mutableFloatStateOf(context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getFloat("voice_pitch", 1.2f)) }
                    var voiceRate by remember { mutableFloatStateOf(context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getFloat("voice_rate", 0.85f)) }
                    var debounceTime by remember { mutableLongStateOf(context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getLong("debounce_time", 500L)) }
                    var autoReturnHome by remember { mutableStateOf(context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getBoolean("auto_return_home", true)) }

                    var isKioskMode by remember { mutableStateOf(false) }
                    val activity = context as? android.app.Activity

                    var usageStats by remember { mutableStateOf(loadStats(context)) }

                    // Modélisation : cases à toucher dans l'ordre pour atteindre un mot (dossiers puis mot)
                    var guideSteps by remember { mutableStateOf<List<AACCell>>(emptyList()) }
                    var guideStep by remember { mutableIntStateOf(0) }
                    val guideTarget = guideSteps.lastOrNull()
                    val guidedId = guideSteps.getOrNull(guideStep)?.id
                    val advanceGuide: (AACCell) -> Unit = { cell ->
                        if (cell.id == guidedId) {
                            guideStep++
                            if (guideStep >= guideSteps.size) { guideSteps = emptyList(); guideStep = 0 }
                        }
                    }

                    // Formes du mot (appui long hors mode admin) : picto concerné, ses formes, et s'il vient de la grille
                    var wordFormsCell by remember { mutableStateOf<AACCell?>(null) }
                    var wordForms by remember { mutableStateOf<List<WordForm>>(emptyList()) }
                    var wordFormsFromGrid by remember { mutableStateOf(false) }
                    val openWordForms: (AACCell, Boolean) -> Unit = { cell, fromGrid ->
                        val forms = if (cell.isFolder || cell.isQuickPhrase) emptyList() else FrenchMorphology.wordForms(cell.label)
                        if (forms.size > 1) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            wordFormsCell = cell; wordForms = forms; wordFormsFromGrid = fromGrid
                        }
                    }
                    var showStatsDialog by remember { mutableStateOf(false) }

                    // Détermine les boutons à afficher selon si on est dans un dossier ou non
                    val currentButtons = if (navigationStack.isEmpty()) boutons else navigationStack.last().content
                    val stackSize = navigationStack.size

                    var sidebarRect by remember { mutableStateOf(value = Rect.Zero) }
                    var quickFiresRect by remember { mutableStateOf(value = Rect.Zero) }
                    val gridCellsBounds = remember { mutableMapOf<AACCell, Rect>() }
                    val dndState = LocalDragAndDropState.current
                    
                    val gridState = rememberLazyGridState()
                    var gridRect by remember { mutableStateOf(Rect.Zero) }
                    
                    val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()

                    // --- LOGIQUE D'INITIALISATION ---
                    
                    // --- RÉCUPÉRATION DES IMAGES MANQUANTES (Pictos ARASAAC) ---
                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) {
                            // Mise à jour asynchrone pour les boutons qui ont le flag "FETCH"
                            updateCellsWithImages(boutons, coreWords, quickFires)
                        }
                    }

                    // --- GESTION DU FICHIER DE SAUVEGARDE ---
                    val openDocLauncher =
                        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                            uri?.let {
                                try {
                                    val json = context.contentResolver.openInputStream(it)?.reader()?.use { reader -> reader.readText() } ?: return@let
                                    val backup = parseBackup(json)
                                    navigationStack.clear() // Les dossiers ouverts n'existent plus après la restauration
                                    boutons.clear(); boutons.addAll(backup.buttons)
                                    backup.coreWords?.let { list -> coreWords.clear(); coreWords.addAll(list) }
                                    backup.quickFires?.let { list -> quickFires.clear(); quickFires.addAll(list) }
                                    saveButtons(boutons); saveCoreWords(coreWords); saveQuickFires(quickFires)
                                } catch (e: Exception) {
                                    Log.e("Nina-Debug", "Restauration impossible", e)
                                    android.widget.Toast.makeText(context, "Fichier de sauvegarde invalide", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }

                    // Boucle d'Auto-Scroll à 100 FPS
                    LaunchedEffect(dndState.isDragging) {
                        while (dndState.isDragging) {
                            val y = dndState.dragOffset.y
                            if (gridRect.height > 0) {
                                // Seuil Haut : 30% du haut de la grille
                                val topThreshold = gridRect.top + (gridRect.height * 0.30f)
                                // Seuil Bas : 50% (Milieu de la grille) pour rester loin du bas de l'écran
                                val bottomThreshold = gridRect.top + (gridRect.height * 0.50f)

                                if (y < topThreshold) {
                                    val distance = (topThreshold - y).coerceAtLeast(0f)
                                    val speed = (distance / (gridRect.height * 0.30f)) * 50f
                                    gridState.scrollBy(-speed)
                                } else if (y > bottomThreshold) {
                                    // Scroll bas plus réactif car la zone est plus grande
                                    val distance = (y - bottomThreshold).coerceAtLeast(0f)
                                    val speed = (distance / (gridRect.height * 0.50f)) * 70f
                                    gridState.scrollBy(speed)
                                }
                            }
                            kotlinx.coroutines.delay(10)
                        }
                    }

                    // --- LOGIQUE DE DROP ---
                    val onDropRequest: (Offset, AACCell) -> Unit = { screenPos, droppedCell ->
                        val target = dndState.currentTargetCell
                        val mode = dndState.currentDropMode

                        // 1. Est-ce qu'on lâche dans la barre de gauche (Core Words) ?
                        if (sidebarRect.contains(screenPos)) {
                            if (!coreWords.any { it.label == droppedCell.label }) {
                                coreWords.add(droppedCell.copy(id = newCellId(), isQuickPhrase = false)) // Copie : nouvel id
                                saveCoreWords(coreWords)
                            }
                        } 
                        // 2. Est-ce qu'on lâche dans la barre de droite (Quick Fires) ?
                        else if (quickFiresRect.contains(screenPos)) {
                            if (!quickFires.any { it.label == droppedCell.label }) {
                                quickFires.add(droppedCell.copy(id = newCellId(), isQuickPhrase = true)) // Copie : nouvel id
                                saveQuickFires(quickFires)
                            }
                        }
                        else if (target != null && target != droppedCell) {
                            // 3. Logique d'Advanced Drop (Swap / Insert)
                            val draggedIndex = currentButtons.indexOfCell(droppedCell)
                            val targetIndex = currentButtons.indexOfCell(target)
                            
                            if (draggedIndex != -1 && targetIndex != -1) {
                                when (mode) {
                                    DropTargetMode.SWAP -> {
                                        val temp = currentButtons[draggedIndex]
                                        currentButtons[draggedIndex] = currentButtons[targetIndex]
                                        currentButtons[targetIndex] = temp
                                    }
                                    DropTargetMode.INSERT_BEFORE, DropTargetMode.INSERT_AFTER -> {
                                        currentButtons.removeAt(draggedIndex)
                                        var newTargetIndex = currentButtons.indexOfCell(target)
                                        if (mode == DropTargetMode.INSERT_AFTER) {
                                            newTargetIndex += 1
                                        }
                                        currentButtons.add(newTargetIndex, droppedCell.copy(isQuickPhrase = false))
                                    }
                                    else -> {}
                                }
                                saveButtons(boutons)
                            }
                        }
                        
                        // Nettoyage
                        dndState.currentTargetCell = null
                        dndState.currentDropMode = DropTargetMode.NONE
                    }

                    // Modificateur PERMANENT sur le Root
                    val rootModifier = Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) { // Écoute infinie
                                // Lecture prioritaire sans bloquer les enfants (sauf si consume() est appelé)
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                
                                // On agit UNIQUEMENT si un Drag a été initié par un appui long dans une cellule
                                if (dndState.isDragging) {
                                    val change = event.changes.firstOrNull()

                                    if (change == null || !change.pressed) {
                                        Log.d("Nina-Debug", "🏠 Root : Relâché détecté, exécution du Drop")
                                        dndState.draggedCell?.let { onDropRequest(dndState.dragOffset, it) }
                                        dndState.isDragging = false
                                        dndState.draggedCell = null
                                    } else {
                                        // Le doigt bouge ! On met à jour la position
                                        dndState.dragOffset = change.position

                                        // NOUVEAU : Calcul visuel en temps réel
                                        val localOffset = dndState.dragOffset - gridRect.topLeft
                                        var hoveredCell: AACCell? = null
                                        var hoveredRect: Rect? = null
                                        
                                        for ((gridCell, bounds) in gridCellsBounds) {
                                            val localBounds = bounds.translate(-gridRect.topLeft)
                                            if (localBounds.contains(localOffset)) {
                                                hoveredCell = gridCell
                                                hoveredRect = localBounds
                                                break
                                            }
                                        }

                                        if (hoveredCell != null && hoveredCell != dndState.draggedCell) {
                                            dndState.currentTargetCell = hoveredCell
                                            val cellWidth = hoveredRect!!.width
                                            val relativeX = localOffset.x - hoveredRect.left
                                            
                                            dndState.currentDropMode = when {
                                                relativeX < cellWidth * 0.25f -> DropTargetMode.INSERT_BEFORE
                                                relativeX > cellWidth * 0.75f -> DropTargetMode.INSERT_AFTER
                                                else -> DropTargetMode.SWAP
                                            }
                                        } else {
                                            dndState.currentTargetCell = null
                                            dndState.currentDropMode = DropTargetMode.NONE
                                        }

                                        // Crucial : On consomme l'événement pour empêcher la grille de scroller
                                        change.consume() 
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(x = -offsetX.dp, y = -offsetY.dp)
                            .then(rootModifier),
                        color = if (isCviMode) Color.Black else Color(backgroundColor)
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {

                            // 1. COLONNE PRINCIPALE (Barre supérieure + Grille)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                // --- BARRE SUPÉRIEURE (Phrase et Contrôles) ---
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Bouton Play : Prononce la phrase construite
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (phrase.isNotEmpty()) {
                                                val texteBrut = phrase.joinToString(" ") { it.label }
                                                val texteFinal = LocalGrammarEngine.refine(texteBrut)
                                                speakText(texteFinal, voicePitch, voiceRate)
                                                logUsage(UsageType.SENTENCE, texteFinal)
                                            }
                                        },
                                        enabled = phrase.isNotEmpty(),
                                        modifier = Modifier
                                            .size(90.dp)
                                            .background(
                                                if (phrase.isNotEmpty()) Color(0xFF4CAF50) else Color(0xFFE0E0E0), 
                                                CircleShape
                                            )
                                            .border(4.dp, Color.White, CircleShape)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(60.dp))
                                    }

                                    Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { showWordFinder = true }, modifier = Modifier.padding(end = 8.dp)) {
                                                Icon(Icons.Default.Search, contentDescription = "Chercher un mot", tint = Color(0xFF6200EE))
                                            }
                                            IconButton(onClick = { showKeyboardDialog = true }, modifier = Modifier.padding(end = 8.dp)) {
                                                Icon(Icons.Default.Keyboard, contentDescription = "Saisie libre", tint = Color(0xFF6200EE))
                                            }
                                            SentenceBar(sentence = phrase, onClear = { phrase.clear() }, haptic = haptic)
                                        }
                                    }

                                    // Bouton Retour : Navigation dans les dossiers
                                    Box(modifier = Modifier.width(80.dp)) {
                                        if (navigationStack.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    navigationStack.removeAt(navigationStack.size - 1)
                                                },
                                                modifier = Modifier
                                                    .size(70.dp)
                                                    .background(Color.White, CircleShape)
                                                    .border(3.dp, Color(0xFF6200EE), CircleShape)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF6200EE), modifier = Modifier.size(35.dp))
                                            }
                                        }
                                    }
                                }

                                // --- BANDEAU DE MODÉLISATION ---
                                if (guideTarget != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                            .background(Color(0xFFFFF3E0), RoundedCornerShape(16.dp))
                                            .border(2.dp, Color(0xFFFF6D00), RoundedCornerShape(16.dp))
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFFFF6D00))
                                        Text(
                                            "Touche la case orange pour dire « ${guideTarget.label} »  (${guideStep + 1}/${guideSteps.size})",
                                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5D4037),
                                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                                        )
                                        TextButton(onClick = { guideSteps = emptyList(); guideStep = 0 }) { Text("Arrêter") }
                                    }
                                }

                                // Fait défiler la grille jusqu'à la case à toucher
                                LaunchedEffect(guidedId, stackSize) {
                                    val index = currentButtons.indexOfFirst { it.id == guidedId }
                                    if (index >= 0) gridState.animateScrollToItem(index)
                                }

                                // --- ZONE DE CONTENU PRINCIPALE ---
                                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                    
                                    // A. SIDEBAR GAUCHE (Core Words / Raccourcis)
                                    Column(
                                        modifier = Modifier
                                            .width(120.dp)
                                            .fillMaxHeight()
                                            .onGloballyPositioned { sidebarRect = it.boundsInRoot() }
                                            .background(Color(0xFFF5F5F5), RoundedCornerShape(16.dp))
                                            .verticalScroll(rememberScrollState(), enabled = !dndState.isDragging)
                                            .padding(bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        coreWords.forEach { cell ->
                                            var posInRoot by remember { mutableStateOf(Offset.Zero) }
                                            DraggableCellWrapper(cell = cell) {
                                                Box(modifier = Modifier.onGloballyPositioned { posInRoot = it.positionInRoot() }) {
                                                    AACCellView(
                                                        cell = cell, haptic = haptic, debounceTime = debounceTime,
                                                        isAdminMode = isAdminMode, isEditMode = isEditMode, isCviMode = isCviMode, isGuided = cell.id == guidedId,
                                                        onClick = {
                                                            if (isEditMode) {
                                                                cellToEdit = cell
                                                                showEditDialog = true
                                                            } else {
                                                                playCellAudio(cell, voicePitch, voiceRate)
                                                                trackWordUsage(cell, context, { usageStats = it }); advanceGuide(cell)
                                                                if (!cell.isQuickPhrase) phrase.add(cell)
                                                            }
                                                        },
                                                        onLongClick = {
                                                            if (isAdminMode) {
                                                                dndState.draggedCell = cell
                                                                dndState.dragOffset = posInRoot
                                                                dndState.isDragging = true
                                                            } else {
                                                                openWordForms(cell, false) // false : picto des Core Words
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        if (isAdminMode) {
                                            IconButton(
                                                onClick = { isAddingToCore = true; showAddDialog = true },
                                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).border(2.dp, Color.Gray, RoundedCornerShape(24.dp))
                                            ) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray) }
                                        }
                                    }

                                    // Séparateur
                                    Box(modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 8.dp).width(2.dp).background(Color.LightGray.copy(alpha = 0.5f)))

                                    // B. GRILLE CENTRALE (Dossiers et Vocabulaire)
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        AnimatedContent(
                                            targetState = currentButtons,
                                            transitionSpec = {
                                                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                                            },
                                            label = "GridAnimation"
                                        ) { targetButtons ->
                                            // Clés uniques : l'id du picto, suffixé (#2, #3...) par sécurité en cas de doublon pour éviter un crash de LazyGrid
                                            val gridKeys = buildList {
                                                val seen = mutableMapOf<String, Int>()
                                                targetButtons.forEach { cell ->
                                                    val n = (seen[cell.id] ?: 0) + 1
                                                    seen[cell.id] = n
                                                    add(if (n == 1) cell.id else "${cell.id}#$n")
                                                }
                                            }
                                            LazyVerticalGrid(
                                                columns = GridCells.Fixed(columnCount),
                                                state = gridState,
                                                contentPadding = PaddingValues(bottom = 500.dp),
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .onGloballyPositioned { gridRect = it.boundsInRoot() },
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                itemsIndexed(
                                                    items = targetButtons,
                                                    key = { index, _ -> gridKeys[index] }
                                                ) { _, cell ->
                                                    var posInRoot by remember { mutableStateOf(Offset.Zero) }
                                                    // Quand la cellule quitte l'écran (scroll, changement de dossier), on oublie sa position
                                                    DisposableEffect(cell) {
                                                        onDispose { gridCellsBounds.remove(cell) }
                                                    }
                                                    DraggableCellWrapper(
                                                        cell = cell
                                                    ) {
                                                        Box(modifier = Modifier.onGloballyPositioned { 
                                                            posInRoot = it.positionInRoot()
                                                            gridCellsBounds[cell] = it.boundsInRoot()
                                                        }) {
                                                            AACCellView(
                                                                cell = cell, haptic = haptic, debounceTime = debounceTime,
                                                                isAdminMode = isAdminMode, isEditMode = isEditMode, isCviMode = isCviMode, isGuided = cell.id == guidedId,
                                                                onClick = {
                                                                    Log.d("Nina-Debug", "🖱️ Clic Cellule détecté")
                                                                    if (isEditMode) {
                                                                        cellToEdit = cell
                                                                        showEditDialog = true
                                                                    } else {
                                                                        playCellAudio(cell, voicePitch, voiceRate)
                                                                        trackWordUsage(cell, context, { usageStats = it }); advanceGuide(cell)
                                                                        if (cell.isFolder) navigationStack.add(cell)
                                                                        else {
                                                                            if (!cell.isQuickPhrase) phrase.add(cell)
                                                                            // Après le choix d'un mot dans un dossier, on revient à l'accueil (comme TD Snap / Grid 3)
                                                                            if (autoReturnHome) navigationStack.clear()
                                                                        }
                                                                    }
                                                                },
                                                                onLongClick = {
                                                                    if (isAdminMode) {
                                                                        dndState.draggedCell = cell
                                                                        dndState.dragOffset = posInRoot
                                                                        dndState.isDragging = true
                                                                        Log.d("Nina-Debug", "🚀 Drag démarré pour ${cell.label} à $posInRoot")
                                                                    } else {
                                                                        openWordForms(cell, true) // true : picto de la grille
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                    // --- SIDEBAR DROITE (Menu Admin et Phrases Rapides) ---
                    Column(
                        modifier = Modifier.width(110.dp).fillMaxHeight().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                                // Bouton de verrouillage Admin
                                FloatingActionButton(
                                    onClick = {
                                        if (isAdminMode) {
                                            isAdminMode = false
                                            isEditMode = false // On coupe l'édition d'urgence au verrouillage
                                            android.util.Log.d("Nina-Debug", "🔒 Cadenas fermé -> Mode Édition forcé à FALSE")
                                        } else {
                                            showAdminChallengeDialog = true
                                        }
                                    },
                                    shape = CircleShape,
                                    containerColor = if (isAdminMode) Color(0xFFFFEB3B) else Color(0xFF4CAF50)
                                ) { Icon(if (isAdminMode) Icons.Default.LockOpen else Icons.Default.Lock, null) }

                                if (isAdminMode) {
                                    AdminMenu(
                                        showMenu = showMenu,
                                        onMenuToggle = { showMenu = it },
                                        onSettings = { showSettingsDialog = true },
                                        onHelp = { showHelpDialog = true },
                                        onCalibration = { showCalibration = true },
                                        onStats = { showStatsDialog = true },
                                        onAdd = { showAddDialog = true },
                                        onKioskToggle = { 
                                            activity?.let { act ->
                                                if (isKioskMode) { act.stopLockTask(); isKioskMode = false }
                                                else { act.startLockTask(); isKioskMode = true }
                                            }
                                        },
                                        isKioskMode = isKioskMode,
                                        isEditMode = isEditMode,
                                        onEditModeToggle = { isEditMode = it },
                                        onBackup = { shareBackupFile(context, NinaBackup(boutons, coreWords, quickFires)) },
                                        onRestore = { openDocLauncher.launch(arrayOf("application/json")) }
                                    )
                                    // Contrôleur de taille de grille
                                    GridSizeSelector(columnCount, {
                                        columnCount = it
                                        context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).edit().putInt("column_count", it).apply()
                                    })
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                // Zone Quick Fires (Phrases rapides)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .onGloballyPositioned { quickFiresRect = it.boundsInRoot() }
                                        .verticalScroll(rememberScrollState(), enabled = !dndState.isDragging),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    quickFires.forEach { cell ->
                                        var posInRoot by remember { mutableStateOf(Offset.Zero) }
                                        DraggableCellWrapper(cell = cell) {
                                            Box(modifier = Modifier.onGloballyPositioned { posInRoot = it.positionInRoot() }) {
                                                AACCellView(
                                                    cell = cell, haptic = haptic, debounceTime = debounceTime,
                                                    isAdminMode = isAdminMode, isEditMode = isEditMode, isCviMode = isCviMode, isGuided = cell.id == guidedId,
                                                    onClick = {
                                                        if (isEditMode) {
                                                            cellToEdit = cell
                                                            showEditDialog = true
                                                        } else {
                                                            playCellAudio(cell, voicePitch, voiceRate)
                                                            trackWordUsage(cell, context, { usageStats = it }); advanceGuide(cell)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (isAdminMode) {
                                                            dndState.draggedCell = cell
                                                            dndState.dragOffset = posInRoot
                                                            dndState.isDragging = true
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (isAdminMode) {
                                        IconButton(
                                            onClick = { isAddingToQuickFires = true; showAddDialog = true },
                                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).border(2.dp, Color.Gray, RoundedCornerShape(24.dp))
                                        ) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray) }
                                    }
                                }
                            }
                        }
                    }

                    wordFormsCell?.let { baseCell ->
                        WordFormsDialog(
                            cell = baseCell, forms = wordForms,
                            onPick = { form ->
                                // La forme choisie remplace le mot de base dans la phrase, et c'est elle qui est prononcée
                                phrase.add(baseCell.copy(id = newCellId(), label = form.text, spokenText = null, audioPath = null))
                                speakText(form.text, voicePitch, voiceRate)
                                trackWordUsage(baseCell, context, { usageStats = it }); advanceGuide(baseCell)
                                if (wordFormsFromGrid && autoReturnHome) navigationStack.clear()
                                wordFormsCell = null
                            },
                            onDismiss = { wordFormsCell = null }
                        )
                    }

                    // --- GESTION DES BOITES DE DIALOGUE (Overlay) ---
                    DialogManager(
                        showKeyboardDialog = showKeyboardDialog, onKeyboardDismiss = { showKeyboardDialog = false }, onKeyboardValidate = { phrase.add(it); showKeyboardDialog = false },
                        showAdminChallenge = showAdminChallengeDialog, onAdminSuccess = { isAdminMode = true; showAdminChallengeDialog = false }, onAdminDismiss = { showAdminChallengeDialog = false },
                        showAddDialog = showAddDialog, onAddDismiss = { showAddDialog = false; isAddingToCore = false; isAddingToQuickFires = false },
                        onAddConfirm = { nouveau ->
                            if (isAddingToCore) { coreWords.add(nouveau); saveCoreWords(coreWords) }
                            else if (isAddingToQuickFires) { quickFires.add(nouveau.copy(isQuickPhrase = true)); saveQuickFires(quickFires) }
                            else { currentButtons.add(nouveau); saveButtons(boutons) }
                            showAddDialog = false; isAddingToCore = false; isAddingToQuickFires = false
                        },
                        showSettings = showSettingsDialog,
                        currentBg = backgroundColor, currentPitch = voicePitch, currentRate = voiceRate, currentDebounce = debounceTime, currentCviMode = isCviMode, currentAutoReturn = autoReturnHome,
                        onSettingsDismiss = { showSettingsDialog = false },
                        onSettingsSave = { newColor, newPitch, newRate, newDebounce, newCviMode, newAutoReturn ->
                            backgroundColor = newColor; voicePitch = newPitch; voiceRate = newRate; debounceTime = newDebounce; isCviMode = newCviMode; autoReturnHome = newAutoReturn
                            context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).edit()
                                .putLong("bg_color", newColor).putFloat("voice_pitch", newPitch).putFloat("voice_rate", newRate)
                                .putLong("debounce_time", newDebounce).putBoolean("cvi_mode", newCviMode)
                                .putBoolean("auto_return_home", newAutoReturn).apply()
                        },
                        showHelp = showHelpDialog, onHelpDismiss = { showHelpDialog = false },
                        showCalibration = showCalibration, onCalibrationFinished = { x, y ->
                            offsetX = x; offsetY = y
                            context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).edit().putFloat("offset_x", x).putFloat("offset_y", y).apply()
                            showCalibration = false
                        },
                        showWordFinder = showWordFinder, coreWords = coreWords, quickFires = quickFires,
                        onWordFinderShow = { guide ->
                            // On repart de l'accueil et on montre la première case du chemin
                            navigationStack.clear()
                            guideSteps = guide.steps; guideStep = 0
                            showWordFinder = false
                        },
                        onWordFinderDismiss = { showWordFinder = false },
                        showStats = showStatsDialog, usageStats = usageStats, onStatsDismiss = { showStatsDialog = false }, onStatsReset = {
                            usageStats = emptyMap(); saveStats(context, emptyMap())
                            scope.launch(Dispatchers.IO) { UsageLog.clear(context) }
                        },
                        showEditDialog = showEditDialog, cellToEdit = cellToEdit, allButtons = boutons,
                        onEditDelete = { targetCell ->
                            if (coreWords.removeAll { it.id == targetCell.id }) saveCoreWords(coreWords)
                            else if (quickFires.removeAll { it.id == targetCell.id }) saveQuickFires(quickFires)
                            else { currentButtons.removeAll { it.id == targetCell.id }; saveButtons(boutons) }
                            showEditDialog = false
                        },
                        onEditSave = { edited, moveRequested, moveTarget ->
                            scope.launch(Dispatchers.IO) {
                                val newUrl = edited.imageUrl
                                val finalUrl = if (newUrl?.startsWith("http") == true) saveImageLocally(context, newUrl, "${edited.label.trim()}_${System.currentTimeMillis()}") else newUrl
                                withContext(Dispatchers.Main) {
                                    // On modifie d'abord, puis on déplace la version modifiée (sinon les modifications étaient perdues)
                                    val updated = replaceCellInLists(edited.copy(imageUrl = finalUrl), coreWords, currentButtons, quickFires)
                                    if (moveRequested && updated != null) {
                                        moveCell(updated, moveTarget?.content ?: boutons, coreWords, currentButtons, quickFires)
                                    }
                                    saveButtons(boutons); saveCoreWords(coreWords); saveQuickFires(quickFires)
                                    showEditDialog = false
                                }
                            }
                        },
                        onEditDismiss = { showEditDialog = false }
                    )
                }
            }
        }
    }

    // --- FONCTIONS UTILITAIRES ---

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale.FRENCH)
        }
    }

    private fun speakText(text: String, pitch: Float = 1.0f, rate: Float = 1.0f) {
        if (text.isBlank()) return
        val cleanText = text.replace(Regex("[^\\p{L}\\p{Nd}\\s.,!?'-]"), "").trim()
        if (cleanText.isBlank()) return
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    private fun playCellAudio(cell: AACCell, pitch: Float, rate: Float) {
        val hasValidAudio = !cell.audioPath.isNullOrEmpty() && File(cell.audioPath).exists()
        if (hasValidAudio) {
            try {
                android.media.MediaPlayer().apply {
                    setDataSource(cell.audioPath)
                    prepare(); start()
                    setOnCompletionListener { release() }
                }
            } catch (e: Exception) {
                speakText(cell.textToSpeak, pitch, rate)
            }
        } else {
            speakText(cell.textToSpeak, pitch, rate)
        }
    }

    private fun trackWordUsage(cell: AACCell, context: Context, onUpdate: (Map<String, Int>) -> Unit) {
        if (!cell.isFolder) {
            val stats = loadStats(context).toMutableMap()
            stats[cell.label] = (stats[cell.label] ?: 0) + 1
            saveStats(context, stats)
            onUpdate(stats)
            logUsage(UsageType.WORD, cell.label)
        }
    }

    /** Ajoute une ligne datée au journal d'utilisation, sans bloquer l'interface. */
    private fun logUsage(type: UsageType, text: String) {
        val time = java.time.LocalDateTime.now()
        lifecycleScope.launch(Dispatchers.IO) { UsageLog.append(applicationContext, type, text, time) }
    }

    private fun saveButtons(list: List<AACCell>) {
        getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).edit()
            .putString("saved_buttons", GsonInstance.gson.toJson(list)).apply()
    }

    private fun loadButtons(): List<AACCell> {
        val json = getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getString("saved_buttons", null)
        return if (json != null) {
            try { normalizeCells(GsonInstance.gson.fromJson<List<AACCell>>(json, object : TypeToken<List<AACCell>>() {}.type)) } catch (e: Exception) { getStarterPack() }
        } else getStarterPack()
    }

    private fun saveCoreWords(list: List<AACCell>) {
        getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).edit()
            .putString("saved_core_words", GsonInstance.gson.toJson(list)).apply()
    }

    private fun loadCoreWords(): List<AACCell> {
        val json = getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getString("saved_core_words", null)
        return if (json != null) {
            try { normalizeCells(GsonInstance.gson.fromJson<List<AACCell>>(json, object : TypeToken<List<AACCell>>() {}.type)) } catch (e: Exception) { getDefaultCoreWords() }
        } else getDefaultCoreWords()
    }

    private fun saveQuickFires(list: List<AACCell>) {
        getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).edit()
            .putString("saved_quick_fires", GsonInstance.gson.toJson(list)).apply()
    }

    private fun loadQuickFires(): List<AACCell> {
        val json = getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getString("saved_quick_fires", null)
        return if (json != null) {
            try { normalizeCells(GsonInstance.gson.fromJson<List<AACCell>>(json, object : TypeToken<List<AACCell>>() {}.type)) } catch (e: Exception) { getDefaultQuickFires() }
        } else getDefaultQuickFires()
    }

    private fun shareBackupFile(context: Context, backup: NinaBackup) {
        try {
            val file = File(context.cacheDir, "sauvegarde_nina.json")
            file.writeText(GsonInstance.gson.toJson(backup))
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Sauvegarder la grille de Nina via :"))
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDestroy() {
        tts?.shutdown(); super.onDestroy()
    }

    // --- PACK DE DÉMARRAGE ET PAR DÉFAUT ---

    private fun getDefaultCoreWords() = listOf(
        AACCell("Vouloir", 0xFFC8E6C9L, "FETCH"),
        AACCell("Aider", 0xFFC8E6C9L, "FETCH"),
        AACCell("Encore", 0xFFF8BBD0L, "FETCH"),
        AACCell("Fini", 0xFFF8BBD0L, "FETCH"),
        AACCell("Toilettes", 0xFFE1BEE7L, "FETCH")
    )

    private fun getDefaultQuickFires() = listOf(
        AACCell("Bonjour", 0xFFFFF9C4L, "FETCH", isQuickPhrase = true),
        AACCell("Oui", 0xFFC8E6C9L, "FETCH", isQuickPhrase = true),
        AACCell("Non", 0xFFFFCDD2L, "FETCH", isQuickPhrase = true),
        AACCell("Merci", 0xFFBBDEFBL, "FETCH", isQuickPhrase = true)
    )

    private fun getStarterPack(): List<AACCell> {
        val colPersonne = 0xFFFFF9C4L; val colAction = 0xFFC8E6C9L; val colObjet = 0xFFFFCC80L
        val colDesc = 0xFFBBDEFBL; val colSocial = 0xFFF8BBD0L; val colLieu = 0xFFE1BEE7L

        return listOf(
            AACCell("Moi", colPersonne, "FETCH"), AACCell("Toi", colPersonne, "FETCH"),
            AACCell("Vouloir", colAction, "FETCH"), AACCell("Aller", colAction, "FETCH"),
            AACCell("Encore", colSocial, "FETCH"), AACCell("Fini", colSocial, "FETCH"),
            AACCell("Aider", colAction, "FETCH"), AACCell("Avec", colSocial, "FETCH"),
            AACCell("Oui", colSocial, "FETCH"), AACCell("Non", colSocial, "FETCH"),
            AACCell("Actions", colAction, "FETCH", isFolder = true, content = mutableStateListOf(AACCell("Manger", colAction, "FETCH"), AACCell("Boire", colAction, "FETCH"))),
            AACCell("Nourriture", colObjet, "FETCH", isFolder = true, content = mutableStateListOf(AACCell("Eau", colObjet, "FETCH"), AACCell("Gâteau", colObjet, "FETCH"))),
            AACCell("Personnes", colPersonne, "FETCH", isFolder = true, content = mutableStateListOf(AACCell("Maman", colPersonne, "FETCH"), AACCell("Papa", colPersonne, "FETCH"))),
            AACCell("Émotions", colDesc, "FETCH", isFolder = true, content = mutableStateListOf(AACCell("Content", colDesc, "FETCH"), AACCell("Triste", colDesc, "FETCH"))),
            AACCell("Lieux", colLieu, "FETCH", isFolder = true, content = mutableStateListOf(AACCell("Maison", colLieu, "FETCH"), AACCell("École", colLieu, "FETCH"))),
            AACCell("Bonjour", colSocial, "FETCH")
        )
    }
}

// --- SOUS-COMPOSANTS DÉPORTÉS (Pour lisibilité) ---

@Composable
fun AdminMenu(
    showMenu: Boolean, onMenuToggle: (Boolean) -> Unit, onSettings: () -> Unit, onHelp: () -> Unit,
    onCalibration: () -> Unit, onStats: () -> Unit, onAdd: () -> Unit, onKioskToggle: () -> Unit,
    isKioskMode: Boolean, isEditMode: Boolean, onEditModeToggle: (Boolean) -> Unit, onBackup: () -> Unit, onRestore: () -> Unit
) {
    Box {
        IconButton(onClick = { onMenuToggle(true) }, modifier = Modifier.size(60.dp).background(Color.White, CircleShape).border(1.dp, Color.Gray, CircleShape)) 
        { Icon(Icons.Default.MoreVert, null) }
        DropdownMenu(expanded = showMenu, onDismissRequest = { onMenuToggle(false) }) {
            DropdownMenuItem(text = { Text("⚙️ Paramètres") }, onClick = { onMenuToggle(false); onSettings() })
            DropdownMenuItem(text = { Text("❓ Aide & Tutoriel") }, onClick = { onMenuToggle(false); onHelp() })
            DropdownMenuItem(text = { Text("🎯 Calibrer le tactile") }, onClick = { onMenuToggle(false); onCalibration() })
            DropdownMenuItem(text = { Text("📊 Statistiques") }, onClick = { onMenuToggle(false); onStats() })
            DropdownMenuItem(text = { Text(stringResource(R.string.btn_add)) }, leadingIcon = { Icon(Icons.Default.Add, null, tint = Color(0xFF4CAF50)) }, onClick = { onMenuToggle(false); onAdd() })
            DropdownMenuItem(
                text = { Text(if (isEditMode) "Quitter l'édition" else "Édition") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { 
                    onMenuToggle(false)
                    onEditModeToggle(!isEditMode)
                    Log.d("Nina-Debug", "🛠️ MODE ÉDITION CHANGÉ : ${!isEditMode}")
                }
            )
            HorizontalDivider()
            DropdownMenuItem(text = { Text(if (isKioskMode) "🔓 Désactiver Kiosque" else "🔒 Activer Mode Kiosque") }, onClick = { onMenuToggle(false); onKioskToggle() })
            DropdownMenuItem(text = { Text("📤 Partager / Sauvegarder") }, onClick = { onMenuToggle(false); onBackup() })
            DropdownMenuItem(text = { Text(stringResource(R.string.btn_restore)) }, onClick = { onMenuToggle(false); onRestore() })
        }
    }
}

@Composable
fun GridSizeSelector(current: Int, onUpdate: (Int) -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = { if (current < 8) onUpdate(current + 1) }) { Icon(Icons.Default.KeyboardArrowUp, null) }
            Text("$current", fontWeight = FontWeight.Black)
            IconButton(onClick = { if (current > 1) onUpdate(current - 1) }) { Icon(Icons.Default.KeyboardArrowDown, null) }
        }
    }
}

@Composable
fun DialogManager(
    showKeyboardDialog: Boolean, onKeyboardDismiss: () -> Unit, onKeyboardValidate: (AACCell) -> Unit,
    showAdminChallenge: Boolean, onAdminSuccess: () -> Unit, onAdminDismiss: () -> Unit,
    showAddDialog: Boolean, onAddDismiss: () -> Unit, onAddConfirm: (AACCell) -> Unit,
    showSettings: Boolean, currentBg: Long, currentPitch: Float, currentRate: Float, currentDebounce: Long, currentCviMode: Boolean, currentAutoReturn: Boolean,
    onSettingsDismiss: () -> Unit, onSettingsSave: (Long, Float, Float, Long, Boolean, Boolean) -> Unit,
    showHelp: Boolean, onHelpDismiss: () -> Unit,
    showCalibration: Boolean, onCalibrationFinished: (Float, Float) -> Unit,
    showWordFinder: Boolean, coreWords: List<AACCell>, quickFires: List<AACCell>, onWordFinderShow: (WordGuide) -> Unit, onWordFinderDismiss: () -> Unit,
    showStats: Boolean, usageStats: Map<String, Int>, onStatsDismiss: () -> Unit, onStatsReset: () -> Unit,
    showEditDialog: Boolean, cellToEdit: AACCell?, allButtons: List<AACCell>,
    onEditDelete: (AACCell) -> Unit, onEditSave: (AACCell, Boolean, AACCell?) -> Unit, onEditDismiss: () -> Unit
) {
    if (showKeyboardDialog) KeyboardInputDialog(onDismiss = onKeyboardDismiss, onValidate = onKeyboardValidate)
    if (showAdminChallenge) AdminChallengeDialog(onDismiss = onAdminDismiss, onSuccess = onAdminSuccess)
    if (showAddDialog) AddButtonDialog(onDismiss = onAddDismiss, onConfirm = onAddConfirm)
    if (showSettings) SettingsDialog(currentBg, currentPitch, currentRate, currentDebounce, currentCviMode, currentAutoReturn, onSettingsDismiss, onSettingsSave)
    if (showHelp) HelpDialog(onDismiss = onHelpDismiss)
    if (showCalibration) CalibrationTest(onFinished = onCalibrationFinished)
    if (showWordFinder) WordFinderDialog(allButtons = allButtons, coreWords = coreWords, quickFires = quickFires, onShow = onWordFinderShow, onDismiss = onWordFinderDismiss)
    if (showStats) StatsDialog(stats = usageStats, onDismiss = onStatsDismiss, onReset = onStatsReset)
    if (showEditDialog && cellToEdit != null) EditDialog(cellToEdit, allButtons, onDelete = onEditDelete, onSave = onEditSave, onDismiss = onEditDismiss)
}

// Fonctions privées de logique
private suspend fun updateCellsWithImages(vararg lists: MutableList<AACCell>) {
    lists.forEach { list ->
        list.forEachIndexed { i, cell ->
            if (cell.imageUrl == "FETCH") {
                try {
                    val results = RetrofitInstance.arasaacApi.searchPictograms(cell.label.lowercase())
                    if (results.isNotEmpty()) {
                        list[i] = cell.copy(imageUrl = "https://static.arasaac.org/pictograms/${results.first()._id}/${results.first()._id}_300.png")
                    }
                } catch (e: Exception) {}
            }
            if (cell.isFolder) updateCellsWithImages(cell.content)
        }
    }
}

/** Position d'un picto dans la liste, retrouvé par son id (même s'il a été modifié entre-temps). */
private fun List<AACCell>.indexOfCell(cell: AACCell): Int = indexOfFirst { it.id == cell.id }

/**
 * Remplace le picto de même id par sa version modifiée, dans la liste où il se trouve.
 * Renvoie la version modifiée, ou null si le picto n'a pas été trouvé.
 */
private fun replaceCellInLists(edited: AACCell, vararg lists: MutableList<AACCell>): AACCell? {
    var found = false
    lists.forEach { list ->
        val idx = list.indexOfCell(edited)
        if (idx != -1) {
            list[idx] = edited
            found = true
        }
    }
    return if (found) edited else null
}

/**
 * Retire la cellule de la liste où elle se trouve (grille, Core Words ou Quick Fires) et l'ajoute à la destination.
 */
private fun moveCell(cell: AACCell, destination: MutableList<AACCell>, vararg sources: MutableList<AACCell>) {
    val source = sources.firstOrNull { it.indexOfCell(cell) != -1 } ?: return
    if (source === destination) return
    source.removeAt(source.indexOfCell(cell))
    destination.add(cell)
}
