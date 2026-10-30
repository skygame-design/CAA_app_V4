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
import kotlin.math.hypot

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
 * Sélection en deux temps : fixer une zone l'agrandit (l'écran principal est zoomé autour du point fixé),
 * puis fixer le picto agrandi l'active et l'écran revient à la normale.
 */
class GazeZoom {
    var active by mutableStateOf(false)
    var originX by mutableFloatStateOf(0.5f) // Point fixe du zoom, en fraction de l'écran
    var originY by mutableFloatStateOf(0.5f)
    var since = 0L

    companion object {
        const val SCALE = 2f
        /** Délai pour que l'animation du zoom soit finie avant de viser les pictos agrandis. */
        const val SETTLE_MS = 400L
        /** Sans picto activé pendant ce temps, l'écran revient à la normale. */
        const val TIMEOUT_MS = 4000L
        /** Attraction minimale de l'aimant autour de chaque case, quelle que soit sa taille. */
        const val MIN_REACH_CM = 1.3
    }
}

/**
 * Sélection par le regard sur l'écran principal : aimant vers la zone la plus proche, anneau de fixation,
 * puis activation (comme un toucher). Le point brut du regard peut être affiché pour l'adulte.
 * Calibration continue : chaque picto choisi (et non effacé aussitôt) ou touché devient un point de calibration.
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
    samples: List<GazeSample> = emptyList(),
    onSamplesLearned: (List<GazeSample>, Double) -> Unit = { _, _ -> },
    learnedOffsetY: Double = 0.0, // Recentrage vertical déjà appris (enregistré dans la calibration)
    zoom: GazeZoom? = null // Non nul : sélection en deux temps
) {
    // Modèle qui s'enrichit pendant l'utilisation (repart de la calibration enregistrée si elle change)
    // Au retour à l'écran (après une fenêtre), on repart des derniers points appris, pas de la calibration d'origine
    var liveSamples by remember(model) { mutableStateOf(samples) }
    var liveModel by remember(model) { mutableStateOf(GazeModel.fit(samples, compensation = model.compensation) ?: model) }
    val snapshot = remember { GazeSnapshot() }
    val rawPoint = rememberGazePoint(tracker, liveModel, correction, snapshot)
    // Recentrage vertical automatique, appliqué ici pour pouvoir l'ajuster sans relancer les filtres du pointeur
    val vertical = remember(model) { VerticalOffsetLearner(offset = learnedOffsetY) }
    var verticalVersion by remember { mutableIntStateOf(0) }
    val point by remember(vertical) {
        derivedStateOf {
            verticalVersion // Recalculé quand le recentrage change
            rawPoint.value?.let { (x, y) -> x to clamp01(y + vertical.offset) }
        }
    }
    val (widthCm, heightCm) = screenSizeCm()
    val magnet = remember(widthCm, heightCm) {
        GazeMagnet().apply { minReach = GazeZoom.MIN_REACH_CM / heightCm; aspect = widthCm / heightCm }
    }
    val dwell = remember { GazeDwell() }
    var currentKey by remember { mutableStateOf<String?>(null) }
    var currentRect by remember { mutableStateOf<NormRect?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    // Première étape du zoom : point fixé et progression
    var zoomAnchor by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var zoomProgress by remember { mutableFloatStateOf(0f) }
    dwell.dwellMs = dwellMs
    val learning by rememberUpdatedState(learningEnabled)
    val onLearned by rememberUpdatedState(onSamplesLearned)

    // Zoom : les positions enregistrées suivent-elles l'agrandissement ? (sinon on les transforme nous-mêmes)
    var zoomReference by remember { mutableStateOf<Pair<String, Double>?>(null) }
    var boundsFollowZoom by remember { mutableStateOf<Boolean?>(null) }
    fun currentZones(): List<Pair<String, NormRect>> {
        val zones = registry.normalized()
        val z = zoom
        if (z == null || !z.active || boundsFollowZoom != false) return zones.filter { (_, r) -> r.bottom > 0 && r.top < 1 }
        val s = GazeZoom.SCALE.toDouble(); val ox = z.originX.toDouble(); val oy = z.originY.toDouble()
        return zones.map { (k, r) ->
            k to NormRect(ox + (r.left - ox) * s, oy + (r.top - oy) * s, ox + (r.right - ox) * s, oy + (r.bottom - oy) * s)
        }.filter { (_, r) -> r.right > 0 && r.left < 1 && r.bottom > 0 && r.top < 1 }
    }

    /** Mesures des yeux au moment du choix, picto réellement visé, point estimé alors. */
    class Choice(val key: String, val rect: NormRect, val regressors: DoubleArray, val faceSize: Double, val estimated: Pair<Double, Double>)

    fun choiceNow(key: String, rect: NormRect?): Choice? {
        val r = rect ?: currentZones().firstOrNull { it.first == key }?.second ?: return null
        val regs = snapshot.regressors ?: return null
        val p = point ?: return null
        return Choice(key, r, regs.copyOf(), snapshot.faceSize, p)
    }

    // Le choix devient un point de calibration, à condition que le point estimé n'en soit pas trop loin
    // (sinon ce n'était sans doute pas la case regardée)
    fun learnFrom(c: Choice) {
        if (!learning) return
        if (hypot(c.estimated.first - c.rect.centerX, c.estimated.second - c.rect.centerY) > 0.15) return
        vertical.learn(c.estimated.first, c.estimated.second, c.rect.centerX, c.rect.centerY)
        verticalVersion++
        // Point de calibration : là où le modèle doit viser pour que, recentrage compris, le pointeur tombe sur le picto
        val (mx, my) = correction.invert(c.rect.centerX, c.rect.centerY - vertical.offset, c.faceSize)
        val updated = GazeCalibrationPlan.learn(liveSamples, GazeSample(c.regressors, mx, my))
        val refit = GazeModel.fit(updated, compensation = liveModel.compensation) ?: return
        liveSamples = updated; liveModel = refit
        android.util.Log.i("Nina-Regard", "Calibration continue (%s) : %d points, recentrage vertical %.3f".format(c.key, updated.size, vertical.offset))
        onLearned(updated, vertical.offset)
    }
    // Activation par le regard : on attend 2 s pour vérifier qu'elle n'est pas aussitôt effacée (⌫)
    var pending by remember { mutableStateOf<Pair<Choice, Long>?>(null) }
    fun isCorrection(key: String) = key == "effacer_mot"

    DisposableEffect(registry) {
        registry.onTouch = { key ->
            if (isCorrection(key)) pending = null
            else choiceNow(key, null)?.let { learnFrom(it) }
        }
        onDispose { registry.onTouch = null }
    }

    LaunchedEffect(model, selectionEnabled, zoom) {
        var signature = ""
        var lastZoomActivity = 0L
        fun startZoom(x: Double, y: Double, now: Long) {
            val z = zoom ?: return
            z.originX = x.toFloat(); z.originY = y.toFloat()
            zoomReference = registry.normalized().firstOrNull()?.let { (k, r) -> k to r.width }
            boundsFollowZoom = null
            z.since = now; z.active = true; lastZoomActivity = now
            zoomAnchor = null; zoomProgress = 0f
            magnet.reset(); dwell.reset()
            currentKey = null; currentRect = null; progress = 0f
        }
        var lastDiagnostic = 0L
        snapshotFlow { point }.collect { p ->
            val now = android.os.SystemClock.uptimeMillis()
            // Journal de réglage (toutes les 2 s) : où est le pointeur, quelle case, où en est la sélection
            if (now - lastDiagnostic > 2000) {
                lastDiagnostic = now
                android.util.Log.d("Nina-Regard", "Sélection : point=%s, case=%s, anneau=%.0f %%, sélection=%s, zoom=%s, cases=%d, rayon=%.3f, œil fermé ignoré=%d".format(
                    p?.let { "(%.2f ; %.2f)".format(it.first, it.second) } ?: "aucun", currentKey?.substringBefore("_") ?: "aucune",
                    progress * 100, if (selectionEnabled) "oui" else "non",
                    zoom?.let { if (it.active) "agrandi" else "attente (%.0f %%)".format(zoomProgress * 100) } ?: "non",
                    registry.zones.size, snapshot.fixationRadius, snapshot.closedEyeSkipped))
            }
            val z = zoom
            if (z != null && z.active && !selectionEnabled) z.active = false
            pending?.let { (choice, time) -> if (now - time > 2000) { pending = null; learnFrom(choice) } }

            // Sélection en deux temps, première étape : fixer un endroit (n'importe où) pour l'agrandir
            if (z != null && selectionEnabled && !z.active) {
                currentKey = null; currentRect = null; progress = 0f
                if (p == null) { zoomAnchor = null; zoomProgress = 0f; return@collect }
                val anchor = zoomAnchor
                if (anchor == null || hypot(p.first - anchor.first, p.second - anchor.second) > 0.08) {
                    zoomAnchor = p; lastZoomActivity = maxOf(now, lastZoomActivity); zoomProgress = 0f
                } else {
                    zoomProgress = ((now - lastZoomActivity).toFloat() / dwellMs).coerceIn(0f, 1f)
                    if (zoomProgress >= 1f) startZoom(anchor.first, anchor.second, now)
                }
                return@collect
            }
            if (z != null && z.active) {
                // Attendre la fin de l'animation, puis vérifier une fois si les positions suivent l'agrandissement
                if (now - z.since < GazeZoom.SETTLE_MS) return@collect
                if (boundsFollowZoom == null) {
                    boundsFollowZoom = zoomReference?.let { (k, w0) ->
                        registry.normalized().firstOrNull { it.first == k }?.second?.width?.let { it > w0 * 1.5 }
                    } ?: true
                    android.util.Log.i("Nina-Regard", "Zoom : positions des cases ${if (boundsFollowZoom == true) "suivies par Compose" else "transformées par l'app"}")
                }
                if (now - lastZoomActivity > GazeZoom.TIMEOUT_MS) {
                    z.active = false; zoomAnchor = null; magnet.reset(); dwell.reset()
                    currentKey = null; currentRect = null; progress = 0f
                    return@collect
                }
            }

            val zones = currentZones()
            // Les zones ont changé (page, dossier) : on repart de zéro
            val sig = zones.joinToString(",") { it.first }
            if (sig != signature) { signature = sig; magnet.reset(); dwell.reset() }
            val index = if (p == null) null else magnet.update(zones.map { it.second }, p.first, p.second, now)
            currentKey = index?.let { zones.getOrNull(it)?.first }
            currentRect = index?.let { zones.getOrNull(it)?.second }
            if (!selectionEnabled) { progress = 0f; return@collect }
            val (pr, fired) = dwell.update(index, now)
            progress = pr
            if (pr > 0f) lastZoomActivity = now
            // L'anneau qui se remplit est montré sur sa propre case (elle peut rester un instant après un écart)
            if (pr > 0f) dwell.zone?.let { zones.getOrNull(it) }?.let { currentKey = it.first; currentRect = it.second }
            if (fired != null) zones.getOrNull(fired)?.let { (key, rect) ->
                pending = if (isCorrection(key)) null else choiceNow(key, rect)?.let { it to now }
                registry.zones[key]?.onActivate?.invoke()
                // Picto choisi : l'écran revient à la normale, avec un petit délai avant le prochain zoom
                if (z != null && z.active) {
                    z.active = false; magnet.reset(); dwell.reset()
                    currentKey = null; currentRect = null; progress = 0f
                    lastZoomActivity = now + 800
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        // Zone visée : cadre orange et anneau de fixation au centre
        val r = currentRect
        if (currentKey != null && r != null) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((r.left * w).toInt(), (r.top * h).toInt()) }
                    .size(with(density) { (r.width * w).toFloat().toDp() }, with(density) { (r.height * h).toFloat().toDp() })
                    .border(6.dp, Color(0xCCFF6D00), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (selectionEnabled && progress > 0f) FixationRing(progress)
            }
        }
        // Première étape du zoom : anneau bleu au point fixé
        val a = zoomAnchor
        if (a != null && zoomProgress > 0.1f) {
            val ringPx = with(density) { 72.dp.toPx() }
            Box(modifier = Modifier.offset { IntOffset((a.first * w - ringPx / 2).toInt(), (a.second * h - ringPx / 2).toInt()) }) {
                FixationRing(zoomProgress, color = Color(0xFF1E88E5))
            }
        }
    }
    if (showRawPoint) GazeCursor(point)
}

@Composable
private fun FixationRing(progress: Float, color: Color = Color(0xFFFF6D00)) {
    Box(modifier = Modifier.size(72.dp).background(Color(0x99FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(64.dp), color = color, strokeWidth = 8.dp, trackColor = color.copy(alpha = 0.2f))
    }
}
