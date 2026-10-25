package com.example.nina

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Une zone regardable : sa position à l'écran (pixels) et l'action déclenchée par la fixation. */
class GazeZone(val bounds: Rect, val onActivate: () -> Unit)

/**
 * Registre des zones regardables de l'écran principal (pictos, Accueil, Play, pages…).
 * Chaque élément s'y inscrit avec Modifier.gazeZone ; la calibration y prend ses cibles,
 * et la sélection par le regard y cherche la zone visée.
 */
class GazeZoneRegistry {
    val zones = mutableStateMapOf<String, GazeZone>()
    var rootWidth by mutableIntStateOf(1)
    var rootHeight by mutableIntStateOf(1)

    /** Appelé quand une zone est touchée du doigt (la sélection par le regard en tire la correction du décalage). */
    var onTouch: ((String) -> Unit)? = null

    /** Zones en coordonnées normalisées, triées par clé (ordre stable). */
    fun normalized(): List<Pair<String, NormRect>> =
        zones.entries.sortedBy { it.key }.map { (key, z) ->
            key to NormRect(
                z.bounds.left / rootWidth.toDouble(), z.bounds.top / rootHeight.toDouble(),
                z.bounds.right / rootWidth.toDouble(), z.bounds.bottom / rootHeight.toDouble()
            )
        }.filter { (_, r) -> r.width > 0.005 && r.height > 0.005 && r.right > 0 && r.left < 1 }

    /** Centres des zones : les cibles de la calibration « sur les pictos ». */
    fun centers() = normalized().map { (_, r) -> r.centerX to r.centerY }
}

val LocalGazeRegistry = staticCompositionLocalOf<GazeZoneRegistry?> { null }

/**
 * Inscrit l'élément comme zone regardable. enabled = false (bouton inactif, picto masqué) : il n'est pas inscrit.
 * La position n'est mise à jour que si elle change (pas de recompositions inutiles).
 */
fun Modifier.gazeZone(key: String, enabled: Boolean = true, onActivate: () -> Unit): Modifier = composed {
    val registry = LocalGazeRegistry.current
    val action by rememberUpdatedState(onActivate)
    val stableAction = remember { { action() } }
    DisposableEffect(registry, key, enabled) {
        onDispose { registry?.zones?.remove(key) }
    }
    if (registry == null || !enabled) Modifier
    else Modifier
        .onGloballyPositioned { coords ->
            val b = coords.boundsInRoot()
            if (registry.zones[key]?.bounds != b) registry.zones[key] = GazeZone(b, stableAction)
        }
        // Observe les touchers sans les consommer : le picto reste cliquable normalement
        .pointerInput(key) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                registry.onTouch?.invoke(key)
            }
        }
}

/**
 * Sélection par le regard sur l'écran principal : aimant vers la zone la plus proche, anneau de fixation,
 * puis activation (comme un toucher). Le point brut du regard peut être affiché pour l'adulte.
 */
@Composable
fun GazeSelectionOverlay(
    tracker: GazeTracker,
    model: GazeModel,
    correction: GazeCorrection,
    registry: GazeZoneRegistry,
    selectionEnabled: Boolean,
    dwellMs: Long,
    showRawPoint: Boolean,
    learningEnabled: Boolean = selectionEnabled,
    onDriftLearned: (Double, Double) -> Unit = { _, _ -> }
) {
    val rawPoint = rememberGazePoint(tracker, model, correction)
    // Décalage appris en cours d'utilisation, en plus de celui de la calibration (repart de zéro si le profil change)
    val drift = remember(model, correction) { DriftLearner() }
    var driftVersion by remember { mutableIntStateOf(0) }
    val point by remember(drift) {
        derivedStateOf {
            driftVersion // Recalculé quand le décalage appris change
            rawPoint.value?.let { (x, y) -> drift.apply(x, y).let { clamp01(it.first) to clamp01(it.second) } }
        }
    }
    val magnet = remember { GazeMagnet() }
    val dwell = remember { GazeDwell() }
    var currentKey by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    dwell.dwellMs = dwellMs
    val learning by rememberUpdatedState(learningEnabled)
    val latestPoint by rememberUpdatedState(point)
    val onLearned by rememberUpdatedState(onDriftLearned)

    // Apprend du centre de la zone réellement visée ; le point estimé est celui du moment de l'activation
    fun learnFrom(key: String, p: Pair<Double, Double>) {
        val r = registry.normalized().firstOrNull { it.first == key }?.second ?: return
        if (drift.learn(p.first, p.second, r.centerX, r.centerY)) {
            driftVersion++
            android.util.Log.i("Nina-Regard", "Correction auto (%s) : décalage appris (%.3f ; %.3f)".format(key, drift.offsetX, drift.offsetY))
            onLearned(drift.offsetX, drift.offsetY)
        }
    }
    // Activation par le regard : on attend 2 s pour vérifier qu'elle n'est pas aussitôt effacée (⌫)
    var pending by remember { mutableStateOf<Triple<String, Pair<Double, Double>, Long>?>(null) }
    fun isCorrection(key: String) = key == "effacer_mot"

    DisposableEffect(registry, drift) {
        registry.onTouch = { key ->
            if (isCorrection(key)) pending = null
            else latestPoint?.let { p -> if (learning) learnFrom(key, p) }
        }
        onDispose { registry.onTouch = null }
    }

    LaunchedEffect(model, selectionEnabled, drift) {
        var signature = ""
        snapshotFlow { point }.collect { p ->
            val zones = registry.normalized()
            // Les zones ont changé (page, dossier) : on repart de zéro
            val sig = zones.joinToString(",") { it.first }
            if (sig != signature) { signature = sig; magnet.reset(); dwell.reset() }
            val now = android.os.SystemClock.uptimeMillis()
            pending?.let { (key, at, time) -> if (now - time > 2000) { pending = null; if (learning) learnFrom(key, at) } }
            val index = if (p == null) null else magnet.update(zones.map { it.second }, p.first, p.second, now)
            currentKey = index?.let { zones.getOrNull(it)?.first }
            if (!selectionEnabled) { progress = 0f; return@collect }
            val (pr, fired) = dwell.update(index, now)
            progress = pr
            // L'anneau qui se remplit est montré sur sa propre case (elle peut rester un instant après un écart)
            if (pr > 0f) dwell.zone?.let { z -> zones.getOrNull(z)?.first?.let { currentKey = it } }
            if (fired != null) zones.getOrNull(fired)?.first?.let { key ->
                if (isCorrection(key)) pending = null else if (p != null) pending = Triple(key, p, now)
                registry.zones[key]?.onActivate?.invoke()
            }
        }
    }

    // Zone visée : cadre orange et anneau de fixation au centre
    val zone = currentKey?.let { registry.zones[it] }
    if (zone != null) {
        val density = LocalDensity.current
        val b = zone.bounds
        Box(
            modifier = Modifier
                .offset { IntOffset(b.left.toInt(), b.top.toInt()) }
                .size(with(density) { b.width.toDp() }, with(density) { b.height.toDp() })
                .border(6.dp, Color(0xCCFF6D00), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selectionEnabled && progress > 0f) {
                Box(modifier = Modifier.size(72.dp).background(Color(0x99FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(64.dp), color = Color(0xFFFF6D00), strokeWidth = 8.dp, trackColor = Color(0x33FF6D00))
                }
            }
        }
    }
    if (showRawPoint) GazeCursor(point)
}
