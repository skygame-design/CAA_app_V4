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
import androidx.compose.ui.zIndex
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
import androidx.core.view.WindowCompat
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
import androidx.compose.foundation.gestures.detectTapGestures
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

        // Chargeur d'images commun : sait aussi afficher les pictos SVG (en ligne ou enregistrés dans la tablette)
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this).components { add(coil.decode.SvgDecoder.Factory()) }.build()
        )

        // Plein écran : l'app occupe tout l'écran, sans barre d'état ni barre de navigation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars(window)

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
                    
                    var gridRect by remember { mutableStateOf(Rect.Zero) }

                    // Grille paginée (sans défilement) : page affichée, disposition calculée, boutons ◀ ▶
                    var gridPage by remember { mutableIntStateOf(0) }
                    var gridLayout by remember { mutableStateOf(GridLayout(1, 1, 1)) }
                    var previousPageRect by remember { mutableStateOf(Rect.Zero) }
                    var nextPageRect by remember { mutableStateOf(Rect.Zero) }
                    // Chaque dossier, et chaque nouvelle taille de grille, repart de la première page
                    LaunchedEffect(stackSize, columnCount) { gridPage = 0 }

                    // Colonnes latérales paginées (dossiers à gauche, phrases rapides à droite)
                    var leftPage by remember { mutableIntStateOf(0) }
                    var leftLayout by remember { mutableStateOf(GridLayout(1, 1, 1)) }
                    var leftPreviousRect by remember { mutableStateOf(Rect.Zero) }
                    var leftNextRect by remember { mutableStateOf(Rect.Zero) }
                    var rightPage by remember { mutableIntStateOf(0) }
                    var rightLayout by remember { mutableStateOf(GridLayout(1, 1, 1)) }
                    var rightPreviousRect by remember { mutableStateOf(Rect.Zero) }
                    var rightNextRect by remember { mutableStateOf(Rect.Zero) }

                    // Cherche les images des pictos marqués "FETCH" et télécharge dans la tablette celles encore sur le web,
                    // puis enregistre : les pictos restent visibles hors réseau
                    suspend fun fetchAndSaveImages() {
                        withContext(Dispatchers.IO) { updateCellsWithImages(context, boutons, coreWords, quickFires) }
                        saveButtons(boutons); saveCoreWords(coreWords); saveQuickFires(quickFires)
                    }

                    // Grilles prêtes : sauvegarde de l'ancienne disposition pour pouvoir revenir en arrière
                    val prefs = context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE)
                    var showRecommendedDialog by remember { mutableStateOf(false) }
                    var hasPreviousLayout by remember { mutableStateOf(prefs.contains("layout_before_recommended")) }
                    val applyLayout: (NinaBackup) -> Unit = { layout ->
                        navigationStack.clear()
                        boutons.clear(); boutons.addAll(layout.buttons)
                        coreWords.clear(); coreWords.addAll(layout.coreWords.orEmpty())
                        quickFires.clear(); quickFires.addAll(layout.quickFires.orEmpty())
                        saveButtons(boutons); saveCoreWords(coreWords); saveQuickFires(quickFires)
                        gridPage = 0; leftPage = 0; rightPage = 0
                        // Les nouveaux pictos vont chercher leur image ARASAAC
                        scope.launch { fetchAndSaveImages() }
                    }

                    // --- LOGIQUE D'INITIALISATION ---
                    
                    // --- RÉCUPÉRATION DES IMAGES MANQUANTES (Pictos ARASAAC) ---
                    LaunchedEffect(Unit) { fetchAndSaveImages() }

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

                    // Pendant un glisser-déposer : rester 0,7 s sur un bouton de page (◀ ▶ de la grille, ▲ ▼ des colonnes)
                    // tourne la page, pour déplacer un picto vers une autre page
                    LaunchedEffect(dndState.isDragging) {
                        var hovered: Pair<String, Int>? = null
                        var hoverStart = 0L
                        while (dndState.isDragging) {
                            val position = dndState.dragOffset
                            val target = when {
                                nextPageRect.contains(position) -> "grid" to 1
                                previousPageRect.contains(position) -> "grid" to -1
                                leftNextRect.contains(position) -> "left" to 1
                                leftPreviousRect.contains(position) -> "left" to -1
                                rightNextRect.contains(position) -> "right" to 1
                                rightPreviousRect.contains(position) -> "right" to -1
                                else -> null
                            }
                            val now = System.currentTimeMillis()
                            if (target != hovered) {
                                hovered = target; hoverStart = now
                            } else if (target != null && now - hoverStart > 700) {
                                val (zone, direction) = target
                                when (zone) {
                                    "grid" -> gridPage = (gridPage + direction).coerceIn(0, gridLayout.pageCount - 1)
                                    "left" -> leftPage = (leftPage + direction).coerceIn(0, leftLayout.pageCount - 1)
                                    "right" -> rightPage = (rightPage + direction).coerceIn(0, rightLayout.pageCount - 1)
                                }
                                hoverStart = now
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            kotlinx.coroutines.delay(50)
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
                        // Depuis Android 15, l'app est dessinée sous les barres système : on réserve leur place
                        // (barre d'état en haut, barre de navigation à 3 boutons en bas ou sur le côté, encoche)
                        // pour qu'elles ne cachent aucun bouton. Le fond de couleur, lui, va jusqu'aux bords.
                        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        // --- BARRE DU MODE ADULTE (visible seulement cadenas ouvert, hors de la zone de l'enfant) ---
                        if (isAdminMode) {
                            AdminToolbar(
                                showMore = showMenu,
                                onMoreToggle = { showMenu = it },
                                isEditMode = isEditMode,
                                onEditModeToggle = { isEditMode = it },
                                columnCount = columnCount,
                                onColumnCountChange = {
                                    columnCount = it
                                    context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).edit().putInt("column_count", it).apply()
                                },
                                onAddToGrid = { showAddDialog = true },
                                onAddToLeft = { isAddingToCore = true; showAddDialog = true },
                                onAddToQuick = { isAddingToQuickFires = true; showAddDialog = true },
                                onSettings = { showSettingsDialog = true },
                                onStats = { showStatsDialog = true },
                                onHelp = { showHelpDialog = true },
                                onCalibration = { showCalibration = true },
                                isKioskMode = isKioskMode,
                                onKioskToggle = {
                                    activity?.let { act ->
                                        if (isKioskMode) { act.stopLockTask(); isKioskMode = false }
                                        else { act.startLockTask(); isKioskMode = true }
                                    }
                                },
                                onBackup = { shareBackupFile(context, NinaBackup(boutons, coreWords, quickFires)) },
                                onRestore = { openDocLauncher.launch(arrayOf("application/json")) },
                                onRecommendedLayout = { showRecommendedDialog = true },
                                onRestorePreviousLayout = if (!hasPreviousLayout) null else ({
                                    prefs.getString("layout_before_recommended", null)?.let { json ->
                                        try { applyLayout(parseBackup(json)) } catch (e: Exception) { Log.e("Nina-Debug", "Ancienne disposition illisible", e) }
                                    }
                                    prefs.edit().remove("layout_before_recommended").apply()
                                    hasPreviousLayout = false
                                }),
                                onLock = { isAdminMode = false; isEditMode = false }, // On coupe l'édition au verrouillage
                                modifier = Modifier.zIndex(1f) // Par-dessus la barre de phrase : la grille ne change pas de taille
                            )
                        }
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
                                    // Lit la phrase construite (bouton Play ou appui sur la barre)
                                    val speakSentence = {
                                        if (phrase.isNotEmpty()) {
                                            val texteBrut = phrase.joinToString(" ") { it.label }
                                            val texteFinal = LocalGrammarEngine.refine(texteBrut)
                                            speakText(texteFinal, voicePitch, voiceRate)
                                            logUsage(UsageType.SENTENCE, texteFinal)
                                        }
                                    }

                                    // Bouton Play : Prononce la phrase construite
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            speakSentence()
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

                                    SentenceBar(
                                        sentence = phrase,
                                        onSpeak = speakSentence,
                                        onBackspace = { if (phrase.isNotEmpty()) phrase.removeAt(phrase.lastIndex) },
                                        onClear = { phrase.clear() },
                                        haptic = haptic,
                                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                                    )

                                    // Outils de l'adulte (recherche / modélisation, saisie libre) : à l'écart de la phrase, en gris discret
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { showWordFinder = true }) {
                                            Icon(Icons.Default.Search, contentDescription = "Chercher un mot", tint = Color.Gray)
                                        }
                                        IconButton(onClick = { showKeyboardDialog = true }) {
                                            Icon(Icons.Default.Keyboard, contentDescription = "Saisie libre", tint = Color.Gray)
                                        }
                                    }

                                    // Bouton Retour : seulement dans un sous-dossier (au premier niveau, 🏠 Accueil suffit)
                                    Box(modifier = Modifier.width(80.dp)) {
                                        if (navigationStack.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    speakText("Retour", voicePitch, voiceRate)
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

                                // Ouvre la page où se trouve la case à toucher (grille ou colonnes)
                                LaunchedEffect(guidedId, stackSize, gridLayout.perPage, leftLayout.perPage, rightLayout.perPage) {
                                    val index = currentButtons.indexOfFirst { it.id == guidedId }
                                    if (index >= 0) gridPage = index / gridLayout.perPage
                                    val leftIndex = coreWords.indexOfFirst { it.id == guidedId }
                                    if (leftIndex >= 0) leftPage = leftIndex / leftLayout.perPage
                                    val rightIndex = quickFires.indexOfFirst { it.id == guidedId }
                                    if (rightIndex >= 0) rightPage = rightIndex / rightLayout.perPage
                                }

                                // --- ZONE DE CONTENU PRINCIPALE ---
                                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                    
                                    // A. COLONNE DE GAUCHE : 🏠 Accueil (fixe) puis les dossiers, sans défilement (pages ▲ ▼ si besoin)
                                    Column(
                                        modifier = Modifier
                                            .width(120.dp)
                                            .fillMaxHeight()
                                            .padding(bottom = 16.dp)
                                            .onGloballyPositioned { sidebarRect = it.boundsInRoot() },
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        HomeCell(
                                            isHome = navigationStack.isEmpty(),
                                            isGuided = false,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                speakText("Accueil", voicePitch, voiceRate)
                                                navigationStack.clear()
                                                gridPage = 0 // Retour à la première page, même si l'on était déjà à l'accueil
                                            },
                                            modifier = Modifier.fillMaxWidth().height(80.dp)
                                        )
                                        val leftItemCount = coreWords.size // Pas de case « + » : la colonne reste identique en mode enfant et adulte
                                        PagedColumn(
                                            itemCount = leftItemCount,
                                            page = leftPage,
                                            onPageChange = { leftPage = it },
                                            onLayout = { if (it != leftLayout) leftLayout = it },
                                            onSpeak = { speakText(it, voicePitch, voiceRate) },
                                            onPagerBounds = { previous, next -> leftPreviousRect = previous; leftNextRect = next },
                                            modifier = Modifier.weight(1f).fillMaxWidth()
                                        ) { index, slotModifier ->
                                            val cell = coreWords[index]
                                            run {
                                                var posInRoot by remember { mutableStateOf(Offset.Zero) }
                                                // Le dossier ouvert (premier niveau) est encadré : l'enfant voit où il se trouve
                                                val isOpen = cell.isFolder && navigationStack.firstOrNull()?.id == cell.id
                                                DraggableCellWrapper(cell = cell, modifier = slotModifier) {
                                                    Box(modifier = Modifier
                                                        .fillMaxSize()
                                                        .then(if (isOpen) Modifier.border(4.dp, Color(0xFF4A5D91), RoundedCornerShape(22.dp)).padding(4.dp) else Modifier)
                                                        .onGloballyPositioned { posInRoot = it.positionInRoot() }
                                                    ) {
                                                        AACCellView(
                                                            cell = cell, haptic = haptic, debounceTime = debounceTime,
                                                            isAdminMode = isAdminMode, isEditMode = isEditMode, isCviMode = isCviMode, isGuided = cell.id == guidedId,
                                                            modifier = Modifier.fillMaxSize(),
                                                            onClick = {
                                                                if (isEditMode) {
                                                                    cellToEdit = cell
                                                                    showEditDialog = true
                                                                } else if (cell.isFolder) {
                                                                    // Un dossier de la colonne s'ouvre depuis n'importe où
                                                                    playCellAudio(cell, voicePitch, voiceRate)
                                                                    advanceGuide(cell)
                                                                    navigationStack.clear()
                                                                    navigationStack.add(cell)
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
                                                                    openWordForms(cell, false) // false : picto de la colonne
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
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
                                            // Grille sans défilement : de 1 à 8 pictos par rangée, des pages si tout ne tient pas
                                            PagedGrid(
                                                cells = targetButtons,
                                                columns = columnCount,
                                                page = gridPage,
                                                onPageChange = { gridPage = it },
                                                onLayout = { if (it != gridLayout) gridLayout = it },
                                                onSpeak = { speakText(it, voicePitch, voiceRate) },
                                            onPagerBounds = { previous, next -> previousPageRect = previous; nextPageRect = next },
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(bottom = 16.dp)
                                                    .onGloballyPositioned { gridRect = it.boundsInRoot() }
                                            ) { cell, cellModifier ->
                                                    var posInRoot by remember { mutableStateOf(Offset.Zero) }
                                                    // Quand la cellule quitte l'écran (scroll, changement de dossier), on oublie sa position
                                                    DisposableEffect(cell) {
                                                        onDispose { gridCellsBounds.remove(cell) }
                                                    }
                                                    DraggableCellWrapper(
                                                        cell = cell,
                                                        modifier = cellModifier
                                                    ) {
                                                        Box(modifier = Modifier.fillMaxSize().onGloballyPositioned {
                                                            posInRoot = it.positionInRoot()
                                                            gridCellsBounds[cell] = it.boundsInRoot()
                                                        }) {
                                                            AACCellView(
                                                                cell = cell, haptic = haptic, debounceTime = debounceTime,
                                                                isAdminMode = isAdminMode, isEditMode = isEditMode, isCviMode = isCviMode, isGuided = cell.id == guidedId,
                                                                modifier = Modifier.fillMaxSize(),
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

                    // --- SIDEBAR DROITE (Menu Admin et Phrases Rapides) ---
                    Column(
                        modifier = Modifier.width(110.dp).fillMaxHeight().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                                // Cadenas discret : il faut le maintenir 2 s pour ouvrir le mode adulte
                                HoldLockButton(
                                    isAdminMode = isAdminMode,
                                    onHoldComplete = { showAdminChallengeDialog = true },
                                    onLock = { isAdminMode = false; isEditMode = false }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                // Zone Quick Fires (Phrases rapides) : sans défilement, pages ▲ ▼ si besoin
                                PagedColumn(
                                    itemCount = quickFires.size,
                                    page = rightPage,
                                    onPageChange = { rightPage = it },
                                    onLayout = { if (it != rightLayout) rightLayout = it },
                                    onSpeak = { speakText(it, voicePitch, voiceRate) },
                                            onPagerBounds = { previous, next -> rightPreviousRect = previous; rightNextRect = next },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .onGloballyPositioned { quickFiresRect = it.boundsInRoot() }
                                ) { index, slotModifier ->
                                    val cell = quickFires[index]
                                    run {
                                        var posInRoot by remember { mutableStateOf(Offset.Zero) }
                                        DraggableCellWrapper(cell = cell, modifier = slotModifier) {
                                            Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { posInRoot = it.positionInRoot() }) {
                                                AACCellView(
                                                    cell = cell, haptic = haptic, debounceTime = debounceTime,
                                                    isAdminMode = isAdminMode, isEditMode = isEditMode, isCviMode = isCviMode, isGuided = cell.id == guidedId,
                                                    modifier = Modifier.fillMaxSize(),
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
                                }
                            }
                        }
                        }
                    }

                    if (showRecommendedDialog) {
                        VocabularyPackDialog(
                            currentColumns = columnCount,
                            onDismiss = { showRecommendedDialog = false },
                            onApply = { pack ->
                                // On garde la disposition actuelle pour pouvoir revenir en arrière
                                prefs.edit().putString("layout_before_recommended", GsonInstance.gson.toJson(NinaBackup(boutons.toList(), coreWords.toList(), quickFires.toList()))).apply()
                                hasPreviousLayout = true
                                applyLayout(pack.build())
                                // Taille de grille conseillée pour que les mots essentiels tiennent sur une page
                                columnCount = pack.columns
                                prefs.edit().putInt("column_count", pack.columns).apply()
                                showRecommendedDialog = false
                            }
                        )
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
                                // Image du web : enregistrée dans la tablette ; si le téléchargement échoue, on garde l'adresse (réessai au démarrage)
                                val isNewRemote = isRemoteImage(newUrl)
                                val finalUrl = if (isNewRemote) saveImageLocally(context, newUrl!!, "picto_${edited.id}_${System.currentTimeMillis()}") ?: newUrl else newUrl
                                val finalSource = if (isNewRemote) newUrl else if (newUrl == targetCellImage(edited, coreWords, currentButtons, quickFires)) edited.sourceUrl else null
                                withContext(Dispatchers.Main) {
                                    // On modifie d'abord, puis on déplace la version modifiée (sinon les modifications étaient perdues)
                                    val updated = replaceCellInLists(edited.copy(imageUrl = finalUrl, sourceUrl = finalSource), coreWords, currentButtons, quickFires)
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

    // Android réaffiche les barres après certaines actions (notification, retour dans l'app…) : on les remasque
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars(window)
    }

    override fun onDestroy() {
        tts?.shutdown(); super.onDestroy()
    }

    // --- PACK DE DÉMARRAGE (voir VocabularyPacks pour les grilles prêtes) ---

    private fun getDefaultCoreWords() = VocabularyPacks.default.build().coreWords.orEmpty()
    private fun getDefaultQuickFires() = VocabularyPacks.default.build().quickFires.orEmpty()
    private fun getStarterPack(): List<AACCell> = VocabularyPacks.default.build().buttons
}

// --- SOUS-COMPOSANTS DÉPORTÉS (Pour lisibilité) ---

/**
 * Cadenas discret en haut à droite : petit, gris, sans couleur vive pour ne pas attirer l'enfant.
 * Il faut le maintenir 2 secondes (un anneau se remplit) pour ouvrir le calcul du mode adulte.
 * Un simple appui affiche seulement « Maintenir 2 s ». Cadenas ouvert, un appui le referme.
 */
@Composable
fun HoldLockButton(isAdminMode: Boolean, onHoldComplete: () -> Unit, onLock: () -> Unit) {
    if (isAdminMode) {
        IconButton(onClick = onLock, modifier = Modifier.size(48.dp).background(Color(0xFFFFEB3B), CircleShape)) {
            Icon(Icons.Default.LockOpen, contentDescription = "Verrouiller")
        }
        return
    }

    val scope = rememberCoroutineScope()
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(showHint) { if (showHint) { kotlinx.coroutines.delay(2000); showHint = false } }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        val hold = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, androidx.compose.animation.core.tween(2000, easing = androidx.compose.animation.core.LinearEasing))
                            onHoldComplete()
                            progress.snapTo(0f)
                        }
                        tryAwaitRelease()
                        if (hold.isActive) {
                            // Relâché trop tôt : on n'ouvre rien
                            hold.cancel()
                            scope.launch { progress.snapTo(0f) }
                            showHint = true
                        }
                    })
                }
        ) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF4A5D91),
                trackColor = Color.Transparent,
                strokeWidth = 4.dp
            )
            Icon(Icons.Default.Lock, contentDescription = "Mode adulte (maintenir 2 secondes)", tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
        }
        if (showHint) Text("Maintenir 2 s", fontSize = 11.sp, color = Color.Gray)
    }
}

/**
 * Barre du mode adulte, en haut de l'écran : tous les outils au même endroit,
 * sans pousser les phrases rapides de la colonne de droite.
 */
@Composable
fun AdminToolbar(
    showMore: Boolean, onMoreToggle: (Boolean) -> Unit,
    isEditMode: Boolean, onEditModeToggle: (Boolean) -> Unit,
    columnCount: Int, onColumnCountChange: (Int) -> Unit,
    onAddToGrid: () -> Unit, onAddToLeft: () -> Unit, onAddToQuick: () -> Unit, onSettings: () -> Unit, onStats: () -> Unit, onHelp: () -> Unit,
    onCalibration: () -> Unit, isKioskMode: Boolean, onKioskToggle: () -> Unit,
    onBackup: () -> Unit, onRestore: () -> Unit,
    onRecommendedLayout: () -> Unit, onRestorePreviousLayout: (() -> Unit)?, // null : rien à restaurer
    onLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFF4A5D91)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF3C4))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF8D6E00))
        Text("Mode adulte", fontWeight = FontWeight.Bold, color = Color(0xFF5D4600), modifier = Modifier.padding(end = 8.dp))

        // Ajouter : on choisit la zone (les colonnes n'ont pas de case « + » pour ne pas changer de disposition)
        Box {
            var showAddMenu by remember { mutableStateOf(false) }
            AdminToolButton(Icons.Default.Add, "Ajouter", { showAddMenu = true })
            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                DropdownMenuItem(text = { Text("Dans la grille (dossier ouvert)") }, onClick = { showAddMenu = false; onAddToGrid() })
                DropdownMenuItem(text = { Text("Dans la colonne de gauche (dossiers)") }, onClick = { showAddMenu = false; onAddToLeft() })
                DropdownMenuItem(text = { Text("Dans les phrases rapides") }, onClick = { showAddMenu = false; onAddToQuick() })
            }
        }
        AdminToolButton(Icons.Default.Edit, if (isEditMode) "Édition : oui" else "Édition", { onEditModeToggle(!isEditMode) }, selected = isEditMode)
        AdminToolButton(Icons.Default.Settings, "Paramètres", onSettings)
        AdminToolButton(Icons.Default.BarChart, "Statistiques", onStats)
        AdminToolButton(Icons.AutoMirrored.Filled.Help, "Aide", onHelp)

        // Taille de la grille : pictos par rangée (1 à 8)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(horizontal = 4.dp)
        ) {
            IconButton(onClick = { if (columnCount > 1) onColumnCountChange(columnCount - 1) }, enabled = columnCount > 1) {
                Icon(Icons.Default.Remove, contentDescription = "Moins de pictos par rangée")
            }
            Text("$columnCount par rangée", fontWeight = FontWeight.Bold, color = accent)
            IconButton(onClick = { if (columnCount < 8) onColumnCountChange(columnCount + 1) }, enabled = columnCount < 8) {
                Icon(Icons.Default.Add, contentDescription = "Plus de pictos par rangée")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Outils plus rares
        Box {
            IconButton(onClick = { onMoreToggle(true) }) { Icon(Icons.Default.MoreVert, contentDescription = "Plus d'outils") }
            DropdownMenu(expanded = showMore, onDismissRequest = { onMoreToggle(false) }) {
                DropdownMenuItem(text = { Text("🎯 Calibrer le tactile") }, onClick = { onMoreToggle(false); onCalibration() })
                DropdownMenuItem(text = { Text(if (isKioskMode) "🔓 Désactiver le mode Kiosque" else "🔒 Activer le mode Kiosque") }, onClick = { onMoreToggle(false); onKioskToggle() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("📤 Partager / Sauvegarder") }, onClick = { onMoreToggle(false); onBackup() })
                DropdownMenuItem(text = { Text(stringResource(R.string.btn_restore)) }, onClick = { onMoreToggle(false); onRestore() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("🧩 Grilles prêtes…") }, onClick = { onMoreToggle(false); onRecommendedLayout() })
                onRestorePreviousLayout?.let { restore ->
                    DropdownMenuItem(text = { Text("↩ Revenir à la disposition précédente") }, onClick = { onMoreToggle(false); restore() })
                }
            }
        }

        Button(onClick = onLock, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Verrouiller")
        }
    }
}

@Composable
private fun AdminToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, selected: Boolean = false) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (selected) Color(0xFF4A5D91) else Color.White, contentColor = if (selected) Color.White else Color(0xFF4A5D91))
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
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
    if (showEditDialog && cellToEdit != null) EditDialog(cellToEdit, allFolders(allButtons, coreWords), onDelete = onEditDelete, onSave = onEditSave, onDismiss = onEditDismiss)
}

// Fonctions privées de logique
/**
 * Prépare les images pour un usage hors réseau :
 * 1. les pictos marqués "FETCH" reçoivent la première image ARASAAC trouvée pour leur libellé ;
 * 2. toute image encore sur le web est téléchargée dans la tablette (fichier nommé d'après l'id du picto).
 * En cas d'échec (pas de réseau), l'adresse web est gardée et on réessaiera au prochain démarrage.
 */
private suspend fun updateCellsWithImages(context: Context, vararg lists: MutableList<AACCell>) {
    lists.forEach { list ->
        list.forEachIndexed { i, original ->
            var cell = original
            // Fichier local disparu (sauvegarde restaurée sur une autre tablette) : on repart de l'adresse d'origine
            val local = cell.imageUrl
            if (local != null && local.startsWith("/") && !java.io.File(local).exists() && cell.sourceUrl != null) {
                cell = cell.copy(imageUrl = cell.sourceUrl)
            }
            if (isRemoteImage(cell.imageUrl)) {
                val remote = cell.imageUrl!!
                saveImageLocally(context, remote, "picto_${cell.id}")?.let { path -> cell = cell.copy(imageUrl = path, sourceUrl = remote) }
            }
            // Pas encore d'image, ou image ARASAAC introuvable (certains pictos de la recherche n'ont pas de fichier) :
            // on essaie les résultats ARASAAC suivants pour ce mot, jusqu'à en trouver un qui se télécharge
            val stillRemote = cell.imageUrl
            if (stillRemote == "FETCH" || (stillRemote != null && stillRemote.startsWith("https://static.arasaac.org/"))) {
                try {
                    val candidates = RetrofitInstance.arasaacApi.searchPictograms(cell.label.lowercase())
                        .map { PictoSearch.arasaacUrl(it._id) }
                        .filter { it != stillRemote }
                        .take(5)
                    for (url in candidates) {
                        val path = saveImageLocally(context, url, "picto_${cell.id}")
                        if (path != null) { cell = cell.copy(imageUrl = path, sourceUrl = url); break }
                    }
                } catch (_: Exception) {} // Pas de réseau : on réessaiera au prochain démarrage
            }
            if (cell !== original && i < list.size && list[i].id == original.id) list[i] = cell
            if (cell.isFolder) updateCellsWithImages(context, cell.content)
        }
    }
}

/** Image actuelle du picto (avant modification), pour savoir si l'utilisateur l'a changée. */
private fun targetCellImage(cell: AACCell, vararg lists: List<AACCell>): String? =
    lists.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == cell.id } }?.imageUrl

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
