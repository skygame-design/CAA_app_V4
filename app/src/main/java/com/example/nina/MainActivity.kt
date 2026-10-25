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
    // Enregistrement des grilles dans la tablette
    private val storage by lazy { NinaStorage(this) }
    // Zones regardables de l'écran (pictos, Accueil, Play…) pour la commande oculaire
    private val gazeRegistry = GazeZoneRegistry()
    private var ttsReady = false
    // État de la voix française (hors ligne ou non), lu par l'interface pour prévenir l'adulte
    private var voiceStatus by mutableStateOf(VoiceStatus.UNKNOWN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Chargeur d'images commun : sait aussi afficher les pictos SVG (en ligne ou enregistrés dans la tablette)
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this).components { add(coil.decode.SvgDecoder.Factory()) }.build()
        )

        // L'écran ne se met pas en veille (ni ne se verrouille) tant que l'app est au premier plan :
        // l'enfant doit pouvoir parler à tout moment, sans déverrouiller la tablette
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Plein écran : l'app occupe tout l'écran, sans barre d'état ni barre de navigation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars(window)

        // Initialisation de la synthèse vocale
        tts = TextToSpeech(this, this)
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            CompositionLocalProvider(LocalGazeRegistry provides gazeRegistry) {
            DragAndDropRoot {
                NinaTheme {
                    val context = LocalContext.current
                    val haptic = LocalHapticFeedback.current
                    val scope = rememberCoroutineScope()
                    
                    // --- ÉTATS RÉACTIFS ---
                    
                    // Listes principales des pictogrammes
                    // Chargement unique au démarrage (avec récupération depuis une sauvegarde si besoin)
                    val initial = remember { storage.loadAll() }
                    var loadNotice by remember { mutableStateOf(initial.recoveredFrom != null || initial.lost) }
                    val boutons = remember { mutableStateListOf<AACCell>().apply { addAll(initial.layout.buttons) } }
                    val phrase = remember { mutableStateListOf<AACCell>() }
                    val navigationStack = remember { mutableStateListOf<AACCell>() } // Pile de navigation pour les dossiers
                    val coreWords = remember { mutableStateListOf<AACCell>().apply { addAll(initial.layout.coreWords.orEmpty()) } } // Barre de gauche
                    val quickFires = remember { mutableStateListOf<AACCell>().apply { addAll(initial.layout.quickFires.orEmpty()) } } // Barre de droite

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
                        storage.saveAll(boutons, coreWords, quickFires)
                    }

                    // Grilles prêtes : sauvegarde de l'ancienne disposition pour pouvoir revenir en arrière
                    val prefs = context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE)
                    var showRecommendedDialog by remember { mutableStateOf(false) }
                    var showPrintBoard by remember { mutableStateOf(false) } // Planche imprimable (PDF)
                    var showAutoBackups by remember { mutableStateOf(false) }

                    // --- COMMANDE OCULAIRE ---
                    val gazeTracker = remember { GazeTracker(applicationContext) }
                    DisposableEffect(Unit) { onDispose { gazeTracker.close() } }
                    var showEyeGaze by remember { mutableStateOf(false) }
                    var gazeCursorEnabled by remember { mutableStateOf(GazeStore.cursorEnabled(context)) } // Point brut du regard (pour l'adulte)
                    var gazeSelectionEnabled by remember { mutableStateOf(GazeStore.selectionEnabled(context)) } // Sélection par fixation
                    var gazeDwellMs by remember { mutableLongStateOf(GazeStore.dwellMs(context)) }
                    var gazeEnabled by remember { mutableStateOf(GazeStore.gazeEnabled(context)) } // Interrupteur général
                    val mediaMuted by rememberMediaMuted() // Volume multimédia à zéro : Nina ne serait pas entendue
                    var gazeProfileVersion by remember { mutableIntStateOf(0) } // Change à chaque nouvelle calibration
                    val gazeProfile = remember(gazeProfileVersion) { GazeStore.activeProfile(context) }
                    val gazeModel = remember(gazeProfile) { gazeProfile?.model() }
                    var hasCameraPermission by remember {
                        mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                    }
                    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasCameraPermission = granted }
                    // La caméra ne tourne que si la fenêtre de commande oculaire est ouverte, ou si le regard est utilisé sur l'écran principal
                    val gazeActive = gazeEnabled && (gazeCursorEnabled || gazeSelectionEnabled) && gazeModel != null && !showEyeGaze
                    val needCamera = hasCameraPermission && gazeEnabled && (showEyeGaze || gazeActive)
                    // Grille adaptée au regard et sélection en deux temps (réglages de la fenêtre Commande oculaire)
                    val gazeGrid = remember(gazeProfileVersion) { GazeStore.gazeGrid(context) }
                    val gazeGridColumns = remember(gazeProfileVersion) { GazeStore.gazeGridColumns(context) }
                    val gazeTwoStep = remember(gazeProfileVersion) { GazeStore.twoStep(context) }
                    val gazeGridOn = gazeActive && gazeSelectionEnabled && gazeGrid
                    val gazeZoom = remember { GazeZoom() }
                    LaunchedEffect(gazeTwoStep, gazeActive) { if (!gazeTwoStep || !gazeActive) gazeZoom.active = false }
                    val gazeZoomScale by androidx.compose.animation.core.animateFloatAsState(
                        if (gazeZoom.active) GazeZoom.SCALE else 1f, androidx.compose.animation.core.tween(300), label = "zoomRegard"
                    )
                    DisposableEffect(needCamera) {
                        if (needCamera) gazeTracker.start(this@MainActivity)
                        onDispose { gazeTracker.stop() }
                    }
                    var showVoiceHelp by remember { mutableStateOf(false) }
                    var hasPreviousLayout by remember { mutableStateOf(prefs.contains("layout_before_recommended")) }
                    val applyLayout: (NinaBackup) -> Unit = { layout ->
                        navigationStack.clear()
                        boutons.clear(); boutons.addAll(layout.buttons)
                        coreWords.clear(); coreWords.addAll(layout.coreWords.orEmpty())
                        quickFires.clear(); quickFires.addAll(layout.quickFires.orEmpty())
                        storage.saveAll(boutons, coreWords, quickFires)
                        gridPage = 0; leftPage = 0; rightPage = 0
                        // Les nouveaux pictos vont chercher leur image ARASAAC
                        scope.launch { fetchAndSaveImages() }
                    }

                    // --- LOGIQUE D'INITIALISATION ---
                    
                    // Grille récupérée (données illisibles) : on garde une copie des données abîmées, puis on enregistre la grille récupérée
                    LaunchedEffect(Unit) {
                        if (initial.recoveredFrom != null || initial.lost) {
                            withContext(Dispatchers.IO) { storage.keepUnreadableData() }
                            storage.saveAll(boutons, coreWords, quickFires)
                        } else {
                            withContext(Dispatchers.IO) { storage.autoBackupNow() } // Sauvegarde du jour dès l'ouverture
                        }
                    }

                    // --- RÉCUPÉRATION DES IMAGES MANQUANTES (Pictos ARASAAC) ---
                    LaunchedEffect(Unit) { fetchAndSaveImages() }

                    // --- GESTION DU FICHIER DE SAUVEGARDE ---
                    val openDocLauncher =
                        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                            uri?.let {
                                try {
                                    val json = context.contentResolver.openInputStream(it)?.reader()?.use { reader -> reader.readText() } ?: return@let
                                    val backup = parseBackup(json).installEmbeddedPhotos(context)
                                    navigationStack.clear() // Les dossiers ouverts n'existent plus après la restauration
                                    boutons.clear(); boutons.addAll(backup.buttons)
                                    backup.coreWords?.let { list -> coreWords.clear(); coreWords.addAll(list) }
                                    backup.quickFires?.let { list -> quickFires.clear(); quickFires.addAll(list) }
                                    storage.saveAll(boutons, coreWords, quickFires)
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
                                storage.saveCoreWords(coreWords)
                            }
                        } 
                        // 2. Est-ce qu'on lâche dans la barre de droite (Quick Fires) ?
                        else if (quickFiresRect.contains(screenPos)) {
                            if (!quickFires.any { it.label == droppedCell.label }) {
                                quickFires.add(droppedCell.copy(id = newCellId(), isQuickPhrase = true)) // Copie : nouvel id
                                storage.saveQuickFires(quickFires)
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
                                storage.saveButtons(boutons)
                            }
                        }
                        
                        // Nettoyage
                        dndState.currentTargetCell = null
                        dndState.currentDropMode = DropTargetMode.NONE
                    }

                    // Le gestionnaire de dépôt change à chaque recomposition (il travaille sur la liste affichée, accueil ou dossier) :
                    // le pointerInput ci-dessous, créé une seule fois, doit toujours appeler la version à jour
                    val currentOnDropRequest by rememberUpdatedState(onDropRequest)

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
                                        dndState.draggedCell?.let { currentOnDropRequest(dndState.dragOffset, it) }
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
                        Box(modifier = Modifier
                            .fillMaxSize()
                            // Sélection en deux temps : l'écran est agrandi autour du point fixé
                            .graphicsLayer {
                                scaleX = gazeZoomScale; scaleY = gazeZoomScale
                                transformOrigin = TransformOrigin(gazeZoom.originX, gazeZoom.originY)
                            }
                            .safeDrawingPadding()) {
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
                                onBackup = { storage.shareBackupFile(NinaBackup(boutons, coreWords, quickFires)) },
                                onRestore = { openDocLauncher.launch(arrayOf("application/json")) },
                                onRecommendedLayout = { showRecommendedDialog = true },
                                onAutoBackups = { showAutoBackups = true },
                                onEyeGaze = { showEyeGaze = true },
                                onPrintBoard = { showPrintBoard = true },
                                voiceWarning = when (voiceStatus) {
                                    VoiceStatus.NETWORK_ONLY -> "Voix française seulement avec internet"
                                    VoiceStatus.MISSING -> "Voix française absente"
                                    else -> null
                                },
                                onVoiceFix = { showVoiceHelp = true },
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
                                            .gazeZone("lire", enabled = phrase.isNotEmpty(), onActivate = speakSentence)
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
                                        isMuted = mediaMuted,
                                        onMutedClick = { showVolumePanel(context) },
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
                                            val goBack: () -> Unit = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                speakText("Retour", voicePitch, voiceRate)
                                                if (navigationStack.isNotEmpty()) navigationStack.removeAt(navigationStack.size - 1)
                                            }
                                            IconButton(
                                                onClick = goBack,
                                                modifier = Modifier
                                                    .size(70.dp)
                                                    .gazeZone("retour", onActivate = goBack)
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
                                        val goHome: () -> Unit = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            speakText("Accueil", voicePitch, voiceRate)
                                            navigationStack.clear()
                                            gridPage = 0 // Retour à la première page, même si l'on était déjà à l'accueil
                                        }
                                        HomeCell(
                                            isHome = navigationStack.isEmpty(),
                                            isGuided = false,
                                            onClick = goHome,
                                            modifier = Modifier.fillMaxWidth().height(80.dp).gazeZone("accueil", onActivate = goHome)
                                        )
                                        val leftItemCount = coreWords.size // Pas de case « + » : la colonne reste identique en mode enfant et adulte
                                        PagedColumn(
                                            itemCount = leftItemCount,
                                            page = leftPage,
                                            onPageChange = { leftPage = it },
                                            onLayout = { if (it != leftLayout) leftLayout = it },
                                            onSpeak = { speakText(it, voicePitch, voiceRate) },
                                            gazeKey = "pages_gauche",
                                            // Regard : cases plus hautes (moins de dossiers par page), plus espacées
                                            minItemHeight = if (gazeGridOn) 130.dp else 80.dp,
                                            gap = if (gazeGridOn) 24.dp else 12.dp,
                                            onPagerBounds = { previous, next -> leftPreviousRect = previous; leftNextRect = next },
                                            modifier = Modifier.weight(1f).fillMaxWidth()
                                        ) { index, slotModifier ->
                                            val cell = coreWords[index]
                                            run {
                                                var posInRoot by remember { mutableStateOf(Offset.Zero) }
                                                // Le dossier ouvert (premier niveau) est encadré : l'enfant voit où il se trouve
                                                val isOpen = cell.isFolder && navigationStack.firstOrNull()?.id == cell.id
                                                // Action du picto : par un toucher ou par le regard (fixation)
                                                val onCellClick: () -> Unit = {
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
                                                }
                                                DraggableCellWrapper(cell = cell, modifier = slotModifier) {
                                                    Box(modifier = Modifier
                                                        .fillMaxSize()
                                                        .then(if (isOpen) Modifier.border(4.dp, Color(0xFF4A5D91), RoundedCornerShape(22.dp)).padding(4.dp) else Modifier)
                                                        .onGloballyPositioned { posInRoot = it.positionInRoot() }
                                                        .gazeZone("gauche_${cell.id}", enabled = !(cell.isHidden && !isAdminMode), onActivate = onCellClick)
                                                    ) {
                                                        AACCellView(
                                                            cell = cell, haptic = haptic, debounceTime = debounceTime,
                                                            isAdminMode = isAdminMode, isEditMode = isEditMode, isCviMode = isCviMode, isGuided = cell.id == guidedId,
                                                            modifier = Modifier.fillMaxSize(),
                                                            onClick = onCellClick,
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
                                                // Regard : moins de pictos par rangée et de grands espaces entre les cases
                                                columns = if (gazeGridOn) minOf(columnCount, gazeGridColumns) else columnCount,
                                                gap = if (gazeGridOn) 40.dp else 16.dp,
                                                page = gridPage,
                                                onPageChange = { gridPage = it },
                                                onLayout = { if (it != gridLayout) gridLayout = it },
                                                onSpeak = { speakText(it, voicePitch, voiceRate) },
                                                gazeKey = "pages_grille",
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
                                                        // Action du picto : par un toucher ou par le regard (fixation)
                                                        val onCellClick: () -> Unit = {
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
                                                        }
                                                        Box(modifier = Modifier.fillMaxSize().onGloballyPositioned {
                                                            posInRoot = it.positionInRoot()
                                                            gridCellsBounds[cell] = it.boundsInRoot()
                                                        }.gazeZone("grille_${cell.id}", enabled = !(cell.isHidden && !isAdminMode), onActivate = onCellClick)) {
                                                            AACCellView(
                                                                cell = cell, haptic = haptic, debounceTime = debounceTime,
                                                                isAdminMode = isAdminMode, isEditMode = isEditMode, isCviMode = isCviMode, isGuided = cell.id == guidedId,
                                                                modifier = Modifier.fillMaxSize(),
                                                                onClick = onCellClick,
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
                                    onLock = { isAdminMode = false; isEditMode = false },
                                    warning = voiceStatus == VoiceStatus.NETWORK_ONLY || voiceStatus == VoiceStatus.MISSING
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                // Zone Quick Fires (Phrases rapides) : sans défilement, pages ▲ ▼ si besoin
                                PagedColumn(
                                    itemCount = quickFires.size,
                                    page = rightPage,
                                    onPageChange = { rightPage = it },
                                    onLayout = { if (it != rightLayout) rightLayout = it },
                                    onSpeak = { speakText(it, voicePitch, voiceRate) },
                                    gazeKey = "pages_droite",
                                    minItemHeight = if (gazeGridOn) 130.dp else 80.dp,
                                    gap = if (gazeGridOn) 24.dp else 12.dp,
                                            onPagerBounds = { previous, next -> rightPreviousRect = previous; rightNextRect = next },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .onGloballyPositioned { quickFiresRect = it.boundsInRoot() }
                                ) { index, slotModifier ->
                                    val cell = quickFires[index]
                                    run {
                                        var posInRoot by remember { mutableStateOf(Offset.Zero) }
                                        // Action de la phrase rapide : par un toucher ou par le regard (fixation)
                                        val onCellClick: () -> Unit = {
                                            if (isEditMode) {
                                                cellToEdit = cell
                                                showEditDialog = true
                                            } else {
                                                playCellAudio(cell, voicePitch, voiceRate)
                                                trackWordUsage(cell, context, { usageStats = it }); advanceGuide(cell)
                                            }
                                        }
                                        DraggableCellWrapper(cell = cell, modifier = slotModifier) {
                                            Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { posInRoot = it.positionInRoot() }
                                                .gazeZone("droite_${cell.id}", enabled = !(cell.isHidden && !isAdminMode), onActivate = onCellClick)) {
                                                AACCellView(
                                                    cell = cell, haptic = haptic, debounceTime = debounceTime,
                                                    isAdminMode = isAdminMode, isEditMode = isEditMode, isCviMode = isCviMode, isGuided = cell.id == guidedId,
                                                    modifier = Modifier.fillMaxSize(),
                                                    onClick = onCellClick,
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

                    // Taille de l'écran, pour exprimer les positions des pictos en fractions de l'écran
                    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned {
                        gazeRegistry.rootWidth = it.size.width.coerceAtLeast(1); gazeRegistry.rootHeight = it.size.height.coerceAtLeast(1)
                    })

                    // Regard sur l'écran principal : case visée encadrée, anneau de fixation, activation du picto.
                    // Pas de sélection en mode adulte, ni quand une fenêtre est ouverte (elle cache les pictos).
                    val anyDialogOpen = showAddDialog || showEditDialog || showKeyboardDialog || showAdminChallengeDialog || showSettingsDialog ||
                        showHelpDialog || showCalibration || showWordFinder || showStatsDialog || wordFormsCell != null ||
                        showRecommendedDialog || showAutoBackups || showVoiceHelp || loadNotice
                    if (gazeActive && gazeModel != null) {
                        GazeSelectionOverlay(
                            tracker = gazeTracker, model = gazeModel, correction = gazeProfile?.correction() ?: GazeCorrection(), registry = gazeRegistry,
                            selectionEnabled = gazeSelectionEnabled && !isAdminMode && !anyDialogOpen,
                            dwellMs = gazeDwellMs,
                            showRawPoint = gazeCursorEnabled,
                            learningEnabled = !isAdminMode && !anyDialogOpen,
                            zoom = if (gazeTwoStep) gazeZoom else null,
                            // Calibration continue : les pictos choisis enrichissent la calibration (enregistrée sans relancer le suivi)
                            samples = gazeProfile?.samples.orEmpty(),
                            onSamplesLearned = { learned -> gazeProfile?.let { GazeStore.saveProfile(context, it.copy(samples = learned)) } }
                        )
                    }

                    if (showEyeGaze) {
                        EyeGazeDialog(
                            tracker = gazeTracker,
                            pictoZones = remember { gazeRegistry.normalized().map { it.second } },
                            hasCameraPermission = hasCameraPermission,
                            onRequestPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            cursorEnabled = gazeCursorEnabled,
                            onCursorEnabledChange = { gazeCursorEnabled = it; GazeStore.setCursorEnabled(context, it) },
                            selectionEnabled = gazeSelectionEnabled,
                            onSelectionEnabledChange = { gazeSelectionEnabled = it; GazeStore.setSelectionEnabled(context, it) },
                            dwellMs = gazeDwellMs,
                            onDwellMsChange = { gazeDwellMs = it; GazeStore.setDwellMs(context, it) },
                            onProfileChanged = { gazeProfileVersion++ },
                            gazeEnabled = gazeEnabled,
                            onGazeEnabledChange = { gazeEnabled = it; GazeStore.setGazeEnabled(context, it) },
                            onDismiss = { showEyeGaze = false }
                        )
                    }

                    if (showAutoBackups) {
                        AutoBackupDialog(
                            onDismiss = { showAutoBackups = false },
                            onRestore = { layout ->
                                // Comme pour les grilles prêtes : la disposition actuelle reste récupérable
                                prefs.edit().putString("layout_before_recommended", GsonInstance.gson.toJson(NinaBackup(boutons.toList(), coreWords.toList(), quickFires.toList()))).apply()
                                hasPreviousLayout = true
                                applyLayout(layout)
                                showAutoBackups = false
                            }
                        )
                    }

                    if (showVoiceHelp) {
                        AlertDialog(
                            onDismissRequest = { showVoiceHelp = false },
                            title = { Text("Voix française hors ligne", fontWeight = FontWeight.Bold) },
                            text = {
                                Text(
                                    (if (voiceStatus == VoiceStatus.MISSING) "La tablette n'a pas de voix française : Nina ne peut pas parler.\n\n"
                                     else "La voix française de la tablette a besoin d'internet : sans Wi-Fi (voiture, parc…), Nina sera muette.\n\n") +
                                    "Pour l'installer (une seule fois, avec du Wi-Fi) :\n" +
                                    "1. Touchez « Installer la voix ».\n" +
                                    "2. Choisissez « Français (France) » et téléchargez la voix.\n" +
                                    "3. Revenez dans Nina : l'avertissement disparaît tout seul.\n\n" +
                                    "Si rien ne s'ouvre : Paramètres Android › Gestion globale › Synthèse vocale › moteur Google › Installer les données vocales."
                                )
                            },
                            confirmButton = { Button(onClick = { showVoiceHelp = false; openVoiceInstall() }) { Text("Installer la voix") } },
                            dismissButton = { TextButton(onClick = { showVoiceHelp = false }) { Text("Plus tard") } }
                        )
                    }

                    if (loadNotice) {
                        AlertDialog(
                            onDismissRequest = { loadNotice = false },
                            title = { Text("Grille récupérée", fontWeight = FontWeight.Bold) },
                            text = {
                                Text(
                                    initial.recoveredFrom?.let {
                                        "La grille enregistrée n'a pas pu être lue. Elle a été récupérée depuis la sauvegarde automatique du " +
                                        "${it.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}. Les changements faits après cette date sont perdus."
                                    } ?: ("La grille enregistrée n'a pas pu être lue et aucune sauvegarde n'existe : la grille de départ a été installée. " +
                                         "Si vous avez un fichier de sauvegarde, utilisez ⋮ › Restaurer.")
                                )
                            },
                            confirmButton = { Button(onClick = { loadNotice = false }) { Text("Compris") } }
                        )
                    }

                    if (showPrintBoard) {
                        PrintBoardDialog(
                            home = boutons, left = coreWords, quick = quickFires, defaultColumns = columnCount,
                            onDismiss = { showPrintBoard = false }
                        )
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
                            if (isAddingToCore) { coreWords.add(nouveau); storage.saveCoreWords(coreWords) }
                            else if (isAddingToQuickFires) { quickFires.add(nouveau.copy(isQuickPhrase = true)); storage.saveQuickFires(quickFires) }
                            else { currentButtons.add(nouveau); storage.saveButtons(boutons) }
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
                            if (coreWords.removeAll { it.id == targetCell.id }) storage.saveCoreWords(coreWords)
                            else if (quickFires.removeAll { it.id == targetCell.id }) storage.saveQuickFires(quickFires)
                            else { currentButtons.removeAll { it.id == targetCell.id }; storage.saveButtons(boutons) }
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
                                    storage.saveAll(boutons, coreWords, quickFires)
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
    }

    // --- FONCTIONS UTILITAIRES ---

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            voiceStatus = tts?.let { VoiceCheck.check(it) } ?: VoiceStatus.MISSING
        } else {
            voiceStatus = VoiceStatus.MISSING
        }
    }

    // Au retour dans l'app (par exemple après avoir installé la voix), on vérifie à nouveau
    override fun onResume() {
        super.onResume()
        if (ttsReady) tts?.let { voiceStatus = VoiceCheck.check(it) }
    }

    /** Ouvre l'installation des voix du moteur vocal (ou, à défaut, les réglages de synthèse vocale d'Android). */
    private fun openVoiceInstall() {
        val install = android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val settings = android.content.Intent("com.android.settings.TTS_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(install) }.recoverCatching { startActivity(settings) }
            .onFailure { android.widget.Toast.makeText(this, "Ouvrez Paramètres › Synthèse vocale pour installer la voix française", android.widget.Toast.LENGTH_LONG).show() }
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

    // Quand on quitte l'app (ou qu'elle passe en arrière-plan) : sauvegarde automatique du jour
    override fun onStop() {
        super.onStop()
        lifecycleScope.launch(Dispatchers.IO) { storage.autoBackupNow() }
    }

    // Android réaffiche les barres après certaines actions (notification, retour dans l'app…) : on les remasque
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars(window)
    }

    override fun onDestroy() {
        tts?.shutdown(); super.onDestroy()
    }
}
