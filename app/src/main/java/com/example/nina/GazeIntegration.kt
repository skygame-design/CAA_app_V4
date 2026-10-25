package com.example.nina

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * Commande oculaire sur l'écran principal : réglages (enregistrés), calibration active, caméra.
 * La caméra ne tourne que si la fenêtre Commande oculaire est ouverte, ou si le regard est utilisé sur l'écran principal.
 */
class GazeController(private val context: Context) {
    val tracker = GazeTracker(context.applicationContext)
    val zoom = GazeZoom() // Sélection en deux temps

    var showDialog by mutableStateOf(false)
    var enabled by mutableStateOf(GazeStore.gazeEnabled(context)) // Interrupteur général
        private set
    var cursorEnabled by mutableStateOf(GazeStore.cursorEnabled(context)) // Point brut du regard (pour l'adulte)
        private set
    var selectionEnabled by mutableStateOf(GazeStore.selectionEnabled(context)) // Sélection par fixation
        private set
    var dwellMs by mutableLongStateOf(GazeStore.dwellMs(context))
        private set
    var hasCameraPermission by mutableStateOf(cameraGranted())

    // Calibration et réglages de la fenêtre Commande oculaire, relus après chaque changement
    var profile by mutableStateOf(GazeStore.activeProfile(context))
        private set
    var model by mutableStateOf(profile?.model())
        private set
    var gridEnabled by mutableStateOf(GazeStore.gazeGrid(context))
        private set
    var gridColumns by mutableIntStateOf(GazeStore.gazeGridColumns(context))
        private set
    var twoStep by mutableStateOf(GazeStore.twoStep(context))
        private set

    /** Regard utilisé sur l'écran principal (pointeur ou sélection), avec une calibration. */
    val active get() = enabled && (cursorEnabled || selectionEnabled) && model != null && !showDialog
    val needCamera get() = hasCameraPermission && enabled && (showDialog || active)
    /** Grille adaptée au regard : moins de pictos par rangée, cases plus grandes et plus espacées. */
    val gridOn get() = active && selectionEnabled && gridEnabled

    private fun cameraGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun updateEnabled(value: Boolean) { enabled = value; GazeStore.setGazeEnabled(context, value) }
    fun updateCursor(value: Boolean) { cursorEnabled = value; GazeStore.setCursorEnabled(context, value) }
    fun updateSelection(value: Boolean) { selectionEnabled = value; GazeStore.setSelectionEnabled(context, value) }
    fun updateDwell(value: Long) { dwellMs = value; GazeStore.setDwellMs(context, value) }

    /** Nouvelle calibration ou réglage modifié dans la fenêtre : on relit tout. */
    fun reload() {
        profile = GazeStore.activeProfile(context)
        model = profile?.model()
        gridEnabled = GazeStore.gazeGrid(context)
        gridColumns = GazeStore.gazeGridColumns(context)
        twoStep = GazeStore.twoStep(context)
    }

    /** Calibration continue : les pictos choisis enrichissent la calibration (enregistrée sans relancer le suivi). */
    fun saveLearnedSamples(samples: List<GazeSample>) {
        profile?.let { GazeStore.saveProfile(context, it.copy(samples = samples)) }
    }
}

@Composable
fun rememberGazeController(): GazeController {
    val context = LocalContext.current
    val controller = remember { GazeController(context) }
    DisposableEffect(Unit) { onDispose { controller.tracker.close() } }
    return controller
}

/** Démarre ou arrête la caméra selon les besoins ; sort du zoom quand la sélection en deux temps n'est plus utilisée. */
@Composable
fun GazeCameraEffect(controller: GazeController, lifecycleOwner: LifecycleOwner) {
    val needCamera = controller.needCamera
    DisposableEffect(needCamera) {
        if (needCamera) controller.tracker.start(lifecycleOwner)
        onDispose { controller.tracker.stop() }
    }
    LaunchedEffect(controller.twoStep, controller.active) {
        if (!controller.twoStep || !controller.active) controller.zoom.active = false
    }
}

/**
 * Regard sur l'écran principal (case visée encadrée, anneau de fixation, activation du picto),
 * et fenêtre Commande oculaire. Pas de sélection en mode adulte, ni quand une fenêtre est ouverte (elle cache les pictos).
 */
@Composable
fun GazeLayer(controller: GazeController, registry: GazeZoneRegistry, isAdminMode: Boolean, anyDialogOpen: Boolean) {
    val model = controller.model
    if (controller.active && model != null) {
        GazeSelectionOverlay(
            tracker = controller.tracker, model = model, correction = controller.profile?.correction() ?: GazeCorrection(), registry = registry,
            selectionEnabled = controller.selectionEnabled && !isAdminMode && !anyDialogOpen,
            dwellMs = controller.dwellMs,
            showRawPoint = controller.cursorEnabled,
            learningEnabled = !isAdminMode && !anyDialogOpen,
            zoom = if (controller.twoStep) controller.zoom else null,
            samples = controller.profile?.samples.orEmpty(),
            onSamplesLearned = controller::saveLearnedSamples
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        controller.hasCameraPermission = granted
    }
    if (controller.showDialog) {
        EyeGazeDialog(
            tracker = controller.tracker,
            pictoZones = remember { registry.normalized().map { it.second } },
            hasCameraPermission = controller.hasCameraPermission,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            cursorEnabled = controller.cursorEnabled,
            onCursorEnabledChange = controller::updateCursor,
            selectionEnabled = controller.selectionEnabled,
            onSelectionEnabledChange = controller::updateSelection,
            dwellMs = controller.dwellMs,
            onDwellMsChange = controller::updateDwell,
            onProfileChanged = controller::reload,
            gazeEnabled = controller.enabled,
            onGazeEnabledChange = controller::updateEnabled,
            onDismiss = { controller.showDialog = false }
        )
    }
}
