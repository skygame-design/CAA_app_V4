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
                    // Positions des cases visibles de chaque zone (pour savoir quelle case est survolée pendant un glisser-déposer)
                    val gridCellsBounds = remember { mutableMapOf<AACCell, Rect>() }
                    val leftCellsBounds = remember { mutableMapOf<AACCell, Rect>() }
                    val rightCellsBounds = remember { mutableMapOf<AACCell, Rect>() }
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

                    // --- COMMANDE OCULAIRE (voir GazeIntegration) ---
                    val gaze = rememberGazeController()
                    GazeCameraEffect(gaze, this@MainActivity)
                    val gazeZoomScale by androidx.compose.animation.core.animateFloatAsState(
                        if (gaze.zoom.active) GazeZoom.SCALE else 1f, androidx.compose.animation.core.tween(300), label = "zoomRegard"
                    )
                    val mediaMuted by rememberMediaMuted() // Volume multimédia à zéro : Nina ne serait pas entendue
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

                    // Pendant un glisser-déposer : rester sur un bouton de page tourne la page
                    DragPageTurns(dndState, haptic) {
                        listOf(
                            nextPageRect to { gridPage = (gridPage + 1).coerceIn(0, gridLayout.pageCount - 1) },
                            previousPageRect to { gridPage = (gridPage - 1).coerceIn(0, gridLayout.pageCount - 1) },
                            leftNextRect to { leftPage = (leftPage + 1).coerceIn(0, leftLayout.pageCount - 1) },
                            leftPreviousRect to { leftPage = (leftPage - 1).coerceIn(0, leftLayout.pageCount - 1) },
                            rightNextRect to { rightPage = (rightPage + 1).coerceIn(0, rightLayout.pageCount - 1) },
                            rightPreviousRect to { rightPage = (rightPage - 1).coerceIn(0, rightLayout.pageCount - 1) }
                        )
                    }

                    // --- LOGIQUE DE DROP ---
                    val onDropRequest: (Offset, AACCell) -> Unit = { screenPos, droppedCell ->
                        val overZone = when {
                            sidebarRect.contains(screenPos) -> DropZone.CORE
                            quickFiresRect.contains(screenPos) -> DropZone.QUICK
                            gridRect.contains(screenPos) -> DropZone.GRID
                            else -> null
                        }
                        val outcome = applyDrop(
                            droppedCell, dndState.sourceZone,
                            dndState.currentTargetCell, dndState.currentTargetZone, dndState.currentDropMode, overZone,
                            coreWords = coreWords, quickFires = quickFires, grid = currentButtons
                        )
                        when (outcome) {
                            DropOutcome.CORE -> storage.saveCoreWords(coreWords)
                            DropOutcome.QUICK -> storage.saveQuickFires(quickFires)
                            DropOutcome.GRID -> storage.saveButtons(boutons)
                            DropOutcome.NONE -> {}
                        }
                        dndState.currentTargetCell = null
                        dndState.currentTargetZone = null
                        dndState.currentDropMode = DropTargetMode.NONE
                    }

                    // Le gestionnaire de dépôt change à chaque recomposition (il travaille sur la liste affichée, accueil ou dossier) :
                    // le suivi du doigt, créé une seule fois, appelle toujours la version à jour
                    val currentOnDropRequest by rememberUpdatedState(onDropRequest)
                    val rootModifier = Modifier.dragAndDropTracking(dndState, {
                        listOf(DropZone.CORE to leftCellsBounds, DropZone.GRID to gridCellsBounds, DropZone.QUICK to rightCellsBounds)
                    }) { pos, cell -> currentOnDropRequest(pos, cell) }

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
                                transformOrigin = TransformOrigin(gaze.zoom.originX, gaze.zoom.originY)
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
                                onEyeGaze = { gaze.showDialog = true },
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
                        // Ce qu'il faut pour afficher les cases, et ce que fait un toucher (ou une fixation du regard) dans chaque zone
                        val cellUi = CellUi(haptic, debounceTime, isAdminMode, isEditMode, isCviMode, guidedId, dndState)
                        val editCell: (AACCell) -> Unit = { cell -> cellToEdit = cell; showEditDialog = true }
                        val onFolderColumnClick: (AACCell) -> Unit = { cell ->
                            if (isEditMode) editCell(cell)
                            else if (cell.isFolder) {
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
                        val onGridClick: (AACCell) -> Unit = { cell ->
                            if (isEditMode) editCell(cell)
                            else {
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
                        val onQuickPhraseClick: (AACCell) -> Unit = { cell ->
                            if (isEditMode) editCell(cell)
                            else {
                                playCellAudio(cell, voicePitch, voiceRate)
                                trackWordUsage(cell, context, { usageStats = it }); advanceGuide(cell)
                            }
                        }
                        // Regard : cases plus hautes et plus espacées dans les colonnes
                        val sideItemHeight = if (gaze.gridOn) 130.dp else 80.dp
                        val sideGap = if (gaze.gridOn) 24.dp else 12.dp
                        val speakButton: (String) -> Unit = { speakText(it, voicePitch, voiceRate) }

                        Row(modifier = Modifier.fillMaxSize()) {

                            // 1. COLONNE PRINCIPALE (Barre supérieure + Grille)
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                // Lit la phrase construite (bouton Play ou appui sur la barre)
                                val speakSentence = {
                                    if (phrase.isNotEmpty()) {
                                        val texteBrut = phrase.joinToString(" ") { it.label }
                                        val texteFinal = LocalGrammarEngine.refine(texteBrut)
                                        speakText(texteFinal, voicePitch, voiceRate)
                                        logUsage(UsageType.SENTENCE, texteFinal)
                                    }
                                }
                                TopBar(
                                    phrase = phrase, haptic = haptic, isMuted = mediaMuted,
                                    onSpeak = speakSentence, onMutedClick = { showVolumePanel(context) },
                                    onWordFinder = { showWordFinder = true }, onKeyboard = { showKeyboardDialog = true },
                                    showBack = navigationStack.size > 1,
                                    onBack = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        speakText("Retour", voicePitch, voiceRate)
                                        if (navigationStack.isNotEmpty()) navigationStack.removeAt(navigationStack.size - 1)
                                    }
                                )

                                // --- BANDEAU DE MODÉLISATION ---
                                if (guideTarget != null) {
                                    GuideBanner(guideTarget.label, guideStep, guideSteps.size, onStop = { guideSteps = emptyList(); guideStep = 0 })
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
                                    // A. COLONNE DE GAUCHE : 🏠 Accueil puis les dossiers
                                    FolderColumn(
                                        cells = coreWords, openFolderId = navigationStack.firstOrNull()?.id, isHome = navigationStack.isEmpty(),
                                        ui = cellUi,
                                        paging = PagingUi(leftPage, { leftPage = it }, { if (it != leftLayout) leftLayout = it }, { previous, next -> leftPreviousRect = previous; leftNextRect = next }),
                                        minItemHeight = sideItemHeight, gap = sideGap, onSpeak = speakButton, cellBounds = leftCellsBounds,
                                        onHome = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            speakText("Accueil", voicePitch, voiceRate)
                                            navigationStack.clear()
                                            gridPage = 0 // Retour à la première page, même si l'on était déjà à l'accueil
                                        },
                                        onCellClick = onFolderColumnClick,
                                        onCellLongPress = { openWordForms(it, false) }, // false : picto de la colonne
                                        modifier = Modifier
                                            .width(120.dp)
                                            .fillMaxHeight()
                                            .padding(bottom = 16.dp)
                                            .onGloballyPositioned { sidebarRect = it.boundsInRoot() }
                                    )

                                    // Séparateur
                                    Box(modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 8.dp).width(2.dp).background(Color.LightGray.copy(alpha = 0.5f)))

                                    // B. GRILLE CENTRALE (Dossiers et Vocabulaire)
                                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        CenterGrid(
                                            cells = currentButtons,
                                            // Regard : moins de pictos par rangée et de grands espaces entre les cases
                                            columns = if (gaze.gridOn) minOf(columnCount, gaze.gridColumns) else columnCount,
                                            gap = if (gaze.gridOn) 40.dp else 16.dp,
                                            ui = cellUi,
                                            paging = PagingUi(gridPage, { gridPage = it }, { if (it != gridLayout) gridLayout = it }, { previous, next -> previousPageRect = previous; nextPageRect = next }),
                                            onSpeak = speakButton, cellBounds = gridCellsBounds,
                                            onCellClick = onGridClick,
                                            onCellLongPress = { openWordForms(it, true) }, // true : picto de la grille
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(bottom = 16.dp)
                                                .onGloballyPositioned { gridRect = it.boundsInRoot() }
                                        )
                                    }
                                }
                            }

                            // 2. COLONNE DE DROITE : cadenas et phrases rapides
                            QuickPhrasesColumn(
                                cells = quickFires, ui = cellUi,
                                paging = PagingUi(rightPage, { rightPage = it }, { if (it != rightLayout) rightLayout = it }, { previous, next -> rightPreviousRect = previous; rightNextRect = next }),
                                minItemHeight = sideItemHeight, gap = sideGap, onSpeak = speakButton, cellBounds = rightCellsBounds,
                                lockButton = {
                                    // Cadenas discret : il faut le maintenir 2 s pour ouvrir le mode adulte
                                    HoldLockButton(
                                        isAdminMode = isAdminMode,
                                        onHoldComplete = { showAdminChallengeDialog = true },
                                        onLock = { isAdminMode = false; isEditMode = false },
                                        warning = voiceStatus == VoiceStatus.NETWORK_ONLY || voiceStatus == VoiceStatus.MISSING
                                    )
                                },
                                onCellClick = onQuickPhraseClick,
                                columnModifier = Modifier.onGloballyPositioned { quickFiresRect = it.boundsInRoot() }
                            )
                        }
                        }
                    }

                    // Taille de l'écran, pour exprimer les positions des pictos en fractions de l'écran
                    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned {
                        gazeRegistry.rootWidth = it.size.width.coerceAtLeast(1); gazeRegistry.rootHeight = it.size.height.coerceAtLeast(1)
                    })

                    // Une fenêtre ouverte cache les pictos : pas de sélection par le regard
                    val anyDialogOpen = showAddDialog || showEditDialog || showKeyboardDialog || showAdminChallengeDialog || showSettingsDialog ||
                        showHelpDialog || showCalibration || showWordFinder || showStatsDialog || wordFormsCell != null ||
                        showRecommendedDialog || showAutoBackups || showVoiceHelp || loadNotice
                    GazeLayer(gaze, gazeRegistry, isAdminMode, anyDialogOpen)

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
