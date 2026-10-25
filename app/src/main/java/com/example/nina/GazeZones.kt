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
    else Modifier.onGloballyPositioned { coords ->
        val b = coords.boundsInRoot()
        if (registry.zones[key]?.bounds != b) registry.zones[key] = GazeZone(b, stableAction)
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
    registry: GazeZoneRegistry,
    selectionEnabled: Boolean,
    dwellMs: Long,
    showRawPoint: Boolean
) {
    val point by rememberGazePoint(tracker, model)
    val magnet = remember { GazeMagnet() }
    val dwell = remember { GazeDwell() }
    var currentKey by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    dwell.dwellMs = dwellMs

    LaunchedEffect(model, selectionEnabled) {
        var signature = ""
        snapshotFlow { point }.collect { p ->
            val zones = registry.normalized()
            // Les zones ont changé (page, dossier) : on repart de zéro
            val sig = zones.joinToString(",") { it.first }
            if (sig != signature) { signature = sig; magnet.reset(); dwell.reset() }
            val now = android.os.SystemClock.uptimeMillis()
            val index = if (p == null) null else magnet.update(zones.map { it.second }, p.first, p.second, now)
            currentKey = index?.let { zones.getOrNull(it)?.first }
            if (!selectionEnabled) { progress = 0f; return@collect }
            val (pr, fired) = dwell.update(index, now)
            progress = pr
            if (fired != null) zones.getOrNull(fired)?.first?.let { key -> registry.zones[key]?.onActivate?.invoke() }
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
