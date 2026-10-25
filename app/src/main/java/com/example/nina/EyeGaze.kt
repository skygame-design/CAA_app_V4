package com.example.nina

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.util.Date

/** Taille de l'écran en cm (pour exprimer la précision en cm, plus parlant qu'en pixels). */
@Composable
private fun screenSizeCm(): Pair<Double, Double> {
    val m = LocalContext.current.resources.displayMetrics
    return (m.widthPixels / m.xdpi * 2.54) to (m.heightPixels / m.ydpi * 2.54)
}

/** Les trois façons de calibrer. */
private enum class CalibrationMode(val label: String) {
    PICTOS("Calibration sur les pictos"),   // une cible à l'emplacement de chaque picto (remplace la calibration)
    REFINE("Retouche rapide"),              // 6 pictos, ajoutés à la calibration existante
    GENERAL("Calibration générale")         // 9 points répartis sur l'écran
}

private enum class GazeScreen { HOME, CALIBRATION, CHECK, RESULT, VERIFICATION, TRIAL }

private val Accent = Color(0xFF4A5D91)

/**
 * Commande oculaire : état de la caméra, calibration (sur les pictos, retouche rapide, générale),
 * vérification de la précision, essai sur les pictos, et réglages de la sélection par le regard.
 *
 * @param pictoZones emplacements des pictos et boutons de l'écran principal au moment de l'ouverture
 */
@Composable
fun EyeGazeDialog(
    tracker: GazeTracker,
    pictoZones: List<NormRect>,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    cursorEnabled: Boolean,
    onCursorEnabledChange: (Boolean) -> Unit,
    selectionEnabled: Boolean,
    onSelectionEnabledChange: (Boolean) -> Unit,
    dwellMs: Long,
    onDwellMsChange: (Long) -> Unit,
    onProfileChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(GazeScreen.HOME) }
    var mode by remember { mutableStateOf(CalibrationMode.PICTOS) }
    var profile by remember { mutableStateOf(GazeStore.activeProfile(context)) }
    var pendingSamples by remember { mutableStateOf<List<GazeSample>>(emptyList()) }
    var plannedCount by remember { mutableIntStateOf(0) }
    var checkCm by remember { mutableStateOf<Double?>(null) } // Vérification faite juste après la calibration
    val (widthCm, heightCm) = screenSizeCm()
    val centers = remember(pictoZones) { pictoZones.map { it.centerX to it.centerY } }
    val hasPictos = centers.size >= 5

    fun targetsFor(m: CalibrationMode) = when {
        m == CalibrationMode.GENERAL || !hasPictos -> GazeTargets.calibration9
        m == CalibrationMode.REFINE -> GazeCalibrationPlan.pickRefineTargets(centers, 6)
        else -> GazeCalibrationPlan.pickTargets(centers, max = 18)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        HideSystemBarsInDialog()
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF0F0F5)) {
            when (screen) {
                GazeScreen.HOME -> GazeHome(
                    tracker = tracker, profile = profile, hasCameraPermission = hasCameraPermission,
                    onRequestPermission = onRequestPermission,
                    pictoCount = if (hasPictos) targetsFor(CalibrationMode.PICTOS).size else 0,
                    cursorEnabled = cursorEnabled, onCursorEnabledChange = onCursorEnabledChange,
                    selectionEnabled = selectionEnabled, onSelectionEnabledChange = onSelectionEnabledChange,
                    dwellMs = dwellMs, onDwellMsChange = onDwellMsChange,
                    onCalibrate = { m -> mode = m; screen = GazeScreen.CALIBRATION },
                    onVerify = { screen = GazeScreen.VERIFICATION },
                    onTrial = { screen = GazeScreen.TRIAL },
                    onDismiss = onDismiss
                )
                GazeScreen.CALIBRATION -> {
                    val targets = remember(mode) { targetsFor(mode) }
                    CalibrationRun(
                        tracker = tracker, targets = targets,
                        previousSamples = if (mode == CalibrationMode.REFINE) profile?.samples.orEmpty() else emptyList(),
                        previousModel = remember(profile) { profile?.model() },
                        onFinished = { samples ->
                            plannedCount = targets.size
                            // Retouche : les nouvelles mesures remplacent les anciennes aux mêmes endroits, le reste est gardé
                            pendingSamples = if (mode == CalibrationMode.REFINE) GazeCalibrationPlan.merge(profile?.samples.orEmpty(), samples) else samples
                            checkCm = null
                            screen = if (pendingSamples.size >= 6) GazeScreen.CHECK else GazeScreen.RESULT
                        },
                        onCancel = { screen = GazeScreen.HOME }
                    )
                }
                GazeScreen.CHECK -> {
                    // Vérification immédiate sur 5 pictos, avec la nouvelle calibration : la vraie précision à attendre
                    val model = remember(pendingSamples) { GazeModel.fit(pendingSamples) }
                    if (model == null) screen = GazeScreen.RESULT
                    else VerificationRun(
                        tracker = tracker, model = model,
                        targets = if (hasPictos) GazeCalibrationPlan.pickRefineTargets(centers, 5) else GazeTargets.verification5,
                        widthCm = widthCm, heightCm = heightCm,
                        title = "Vérification de la nouvelle calibration",
                        onResult = { checkCm = it; screen = GazeScreen.RESULT },
                        onClose = { screen = GazeScreen.RESULT }
                    )
                }
                GazeScreen.RESULT -> {
                    val error = remember(pendingSamples) { GazeModel.leaveOneOutError(pendingSamples, widthScale = widthCm, heightScale = heightCm) }
                    val newPoints = if (mode == CalibrationMode.REFINE) pendingSamples.size - (profile?.samples?.size ?: 0) else pendingSamples.size
                    val pendingModel = remember(pendingSamples) { GazeModel.fit(pendingSamples) }
                    CalibrationResult(
                        tracker = tracker, pendingModel = pendingModel,
                        mode = mode, measured = pendingSamples.size, planned = plannedCount, errorCm = error, checkCm = checkCm,
                        refinedFrom = (profile?.checkCm ?: profile?.errorCm).takeIf { mode == CalibrationMode.REFINE },
                        enoughPoints = pendingSamples.size >= 6 && (mode == CalibrationMode.REFINE || newPoints >= 6),
                        onSave = {
                            val saved = GazeProfile("Principal", pendingSamples, System.currentTimeMillis(), error, checkCm, GazeFeatures.FEATURE_VERSION)
                            GazeStore.saveProfile(context, saved)
                            profile = saved
                            onProfileChanged()
                            screen = GazeScreen.HOME
                        },
                        onRetry = { screen = GazeScreen.CALIBRATION },
                        onRecheck = { screen = GazeScreen.CHECK },
                        onCancel = { screen = GazeScreen.HOME }
                    )
                }
                GazeScreen.VERIFICATION, GazeScreen.TRIAL -> {
                    val model = remember(profile) { profile?.model() }
                    if (model == null) screen = GazeScreen.HOME
                    else if (screen == GazeScreen.VERIFICATION) VerificationRun(
                        tracker = tracker, model = model,
                        targets = if (hasPictos) GazeCalibrationPlan.pickRefineTargets(centers, 5) else GazeTargets.verification5,
                        widthCm = widthCm, heightCm = heightCm,
                        onClose = { screen = GazeScreen.HOME }
                    )
                    else PictoTrial(
                        tracker = tracker, model = model, zones = pictoZones, dwellMs = dwellMs,
                        onClose = { screen = GazeScreen.HOME }
                    )
                }
            }
        }
    }
}

@Composable
private fun GazeHome(
    tracker: GazeTracker, profile: GazeProfile?, hasCameraPermission: Boolean, onRequestPermission: () -> Unit,
    pictoCount: Int,
    cursorEnabled: Boolean, onCursorEnabledChange: (Boolean) -> Unit,
    selectionEnabled: Boolean, onSelectionEnabledChange: (Boolean) -> Unit,
    dwellMs: Long, onDwellMsChange: (Long) -> Unit,
    onCalibrate: (CalibrationMode) -> Unit, onVerify: () -> Unit, onTrial: () -> Unit, onDismiss: () -> Unit
) {
    val frame by tracker.frames.collectAsState()
    val error by tracker.error.collectAsState()
    val cameraOk = hasCameraPermission && error == null

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👁 Commande oculaire", fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Fermer") }
        }
        Text(
            "La caméra frontale suit le regard. Tout se passe dans la tablette : aucune image n'est enregistrée ni envoyée. " +
            "Pour de bons résultats : tablette posée sur un support, visage bien éclairé de face, à 40–60 cm de l'écran.",
            fontSize = 15.sp, color = Color.DarkGray
        )

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // État de la caméra, en direct
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Caméra", fontWeight = FontWeight.Bold, color = Accent, fontSize = 18.sp)
                    when {
                        !hasCameraPermission -> {
                            Text("Nina n'a pas l'autorisation d'utiliser la caméra.")
                            Button(onClick = onRequestPermission) { Text("Autoriser la caméra") }
                        }
                        error != null -> Text("⚠️ $error", color = Color(0xFFC62828))
                        frame == null -> Text("Démarrage de la caméra…", color = Color.Gray)
                        else -> {
                            val f = frame!!
                            StatusLine(f.faceDetected, "Visage détecté", "Aucun visage : placez-vous face à la tablette")
                            if (f.faceDetected) StatusLine(f.eyesOpen, "Yeux bien visibles", "Yeux fermés ou cachés")
                            StatusLine(f.brightness > 0.25, "Lumière suffisante", "Trop sombre : éclairez le visage")
                            f.features?.let { feat ->
                                // Taille du visage dans l'image (écart entre les coins extérieurs des yeux) :
                                // mesuré sur la Galaxy Tab A9+ : ≈ 0,18 à 40 cm pour un adulte ; tolère ~25 à ~65 cm, et un visage d'enfant
                                val distance = when {
                                    feat.faceSize > 0.32 -> false to "Trop près de la tablette"
                                    feat.faceSize < 0.10 -> false to "Trop loin de la tablette"
                                    else -> true to "Distance correcte"
                                }
                                StatusLine(distance.first, distance.second, distance.second)
                            }
                        }
                    }
                }
            }
            // Calibration enregistrée
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Calibration", fontWeight = FontWeight.Bold, color = Accent, fontSize = 18.sp)
                    if (profile == null) Text("Pas encore calibrée.", color = Color.Gray)
                    else {
                        Text("${profile.samples.size} points mesurés")
                        Text("Mise à jour le ${DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(profile.updatedAt))}", fontSize = 13.sp, color = Color.Gray)
                        (profile.checkCm ?: profile.errorCm)?.let { Text(precisionText(it), fontWeight = FontWeight.Bold) }
                        if (profile.checkCm != null) Text("(mesurée par la vérification après la calibration)", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        // Calibrations
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Calibrer", fontWeight = FontWeight.Bold, color = Accent, fontSize = 18.sp)
                Text(
                    if (pictoCount > 0) "La calibration se fait aux emplacements des pictos affichés : faites-la avec la taille de grille que Nina utilise. " +
                        "La retouche rapide complète la calibration existante (lumière ou position changée)."
                    else "Les pictos de l'écran n'ont pas pu être repérés : la calibration générale (9 points) sera utilisée.",
                    fontSize = 14.sp, color = Color.DarkGray
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onCalibrate(CalibrationMode.PICTOS) }, enabled = cameraOk && pictoCount > 0, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("Calibrer sur les pictos ($pictoCount points)")
                    }
                    OutlinedButton(onClick = { onCalibrate(CalibrationMode.REFINE) }, enabled = cameraOk && profile != null && pictoCount > 0) { Text("Retouche rapide (6 points)") }
                    TextButton(onClick = { onCalibrate(CalibrationMode.GENERAL) }, enabled = cameraOk) { Text("Calibration générale (9 points)") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onVerify, enabled = profile != null && cameraOk) { Text("Vérifier la précision") }
                    OutlinedButton(onClick = onTrial, enabled = profile != null && cameraOk && pictoCount > 0) { Text("Essai sur les pictos") }
                }
            }
        }

        // Réglages de l'utilisation sur l'écran principal
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sur l'écran de Nina", fontWeight = FontWeight.Bold, color = Accent, fontSize = 18.sp)
                SettingSwitch(
                    "Sélection par le regard",
                    "La case regardée est encadrée en orange ; un anneau se remplit, puis le picto est activé comme par un toucher. Inactif en mode adulte.",
                    selectionEnabled, profile != null && hasCameraPermission, onSelectionEnabledChange
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Durée de fixation", modifier = Modifier.width(170.dp))
                    Slider(
                        value = dwellMs / 1000f, onValueChange = { onDwellMsChange((it * 10).toInt() * 100L) },
                        valueRange = 0.6f..3f, steps = 11, modifier = Modifier.weight(1f)
                    )
                    Text("%.1f s".format(dwellMs / 1000f), modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
                }
                SettingSwitch(
                    "Montrer le point du regard",
                    "Pour l'adulte : un rond orange montre où l'app estime le regard, avant l'aimant vers les cases.",
                    cursorEnabled, profile != null && hasCameraPermission, onCursorEnabledChange
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 13.sp, color = Color.Gray)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun StatusLine(ok: Boolean, okText: String, koText: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(14.dp).background(if (ok) Color(0xFF43A047) else Color(0xFFFF6D00), CircleShape))
        Spacer(modifier = Modifier.width(10.dp))
        Text(if (ok) okText else koText)
    }
}

private fun precisionText(cm: Double): String {
    val v = "%.1f".format(cm)
    return when {
        cm < 2.5 -> "Précision ≈ $v cm : très bien (4 pictos par rangée possibles)"
        cm < 4.0 -> "Précision ≈ $v cm : correcte (préférez 3 pictos par rangée ou moins)"
        else -> "Précision ≈ $v cm : insuffisante (vérifiez lumière, distance, support)"
    }
}

/** Une cible qui pulse, pour attirer le regard de l'enfant. */
@Composable
private fun GazeTarget(x: Double, y: Double, collecting: Boolean) {
    val pulse by rememberInfiniteTransition(label = "cible").animateFloat(
        initialValue = 0.75f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "pulse"
    )
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val size = 100.dp
        val sizePx = with(LocalDensity.current) { size.toPx() }
        val left = (constraints.maxWidth * x - sizePx / 2).toInt()
        val top = (constraints.maxHeight * y - sizePx / 2).toInt()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.offset { IntOffset(left, top) }.size(size).scale(if (collecting) 0.8f else pulse)
                .background(if (collecting) Color(0xFFFFD54F) else Color(0xFFFF8A65), CircleShape)
                .border(6.dp, Color.White, CircleShape)
        ) {
            Text("⭐", fontSize = 40.sp)
        }
    }
}

/** Calibration : les cibles l'une après l'autre ; pour chacune, médiane des images où les yeux sont visibles. */
@Composable
private fun CalibrationRun(
    tracker: GazeTracker, targets: List<Pair<Double, Double>>,
    previousSamples: List<GazeSample>, previousModel: GazeModel?,
    onFinished: (List<GazeSample>) -> Unit, onCancel: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    var collecting by remember { mutableStateOf(false) }
    var waitingForFace by remember { mutableStateOf(false) }
    val frame by tracker.frames.collectAsState()
    // Rond orange pour l'adulte : d'abord la calibration précédente (s'il y en a une),
    // puis la nouvelle dès 6 points mesurés, recalculée après chaque point
    var liveModel by remember { mutableStateOf(previousModel) }

    LaunchedEffect(targets) {
        val samples = mutableListOf<GazeSample>()
        targets.forEachIndexed { i, (tx, ty) ->
            index = i; collecting = false
            delay(800) // Le temps que le regard arrive sur la cible
            collecting = true
            val frames = mutableListOf<DoubleArray>()
            // Jusqu'à 24 images utilisables, au plus 2,5 secondes (clignements ignorés)
            withTimeoutOrNull(2500) {
                tracker.frames.filterNotNull().first { f ->
                    waitingForFace = !f.usable
                    if (f.usable) f.features?.let { frames += it.regressors() }
                    frames.size >= 24 // Arrête la collecte de cette cible
                }
            }
            if (frames.size >= 8) medianFeatures(frames)?.let { samples += GazeSample(it, tx, ty) }
            val soFar = GazeCalibrationPlan.merge(previousSamples, samples)
            if (soFar.size >= 6) GazeModel.fit(soFar)?.let { liveModel = it }
        }
        onFinished(samples)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF263238))) {
        val (tx, ty) = targets[index]
        GazeTarget(tx, ty, collecting)
        liveModel?.let { m ->
            val point by rememberGazePoint(tracker, m)
            GazeCursor(point)
        }
        Text(
            if (waitingForFace && collecting) "Je ne vois pas les yeux… regarde l'étoile !" else "Regarde l'étoile ⭐  (${index + 1} / ${targets.size})",
            color = Color.White, fontSize = 20.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
        )
        if (frame?.faceDetected == false) Text("Aucun visage détecté", color = Color(0xFFFFAB91), modifier = Modifier.align(Alignment.Center))
        Text(
            "Rond orange : où l'app estime le regard (${if (liveModel == null) "apparaît après 6 points" else "mis à jour à chaque point"})",
            color = Color(0xFFFFCC80), fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        )
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text("Annuler", color = Color.White) }
    }
}

@Composable
private fun CalibrationResult(
    tracker: GazeTracker, pendingModel: GazeModel?,
    mode: CalibrationMode, measured: Int, planned: Int, errorCm: Double?, checkCm: Double?, refinedFrom: Double?, enoughPoints: Boolean,
    onSave: () -> Unit, onRetry: () -> Unit, onRecheck: () -> Unit, onCancel: () -> Unit
) {
    // Rond orange : l'adulte peut regarder quelques pictos et juger par lui-même avant d'enregistrer
    pendingModel?.let { m ->
        val point by rememberGazePoint(tracker, m)
        GazeCursor(point)
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("${mode.label} terminée", fontWeight = FontWeight.Bold, fontSize = 28.sp)
        Text(
            if (mode == CalibrationMode.REFINE) "$planned pictos remesurés, $measured points au total dans la calibration"
            else "$measured points mesurés sur $planned",
            fontSize = 18.sp
        )
        when {
            !enoughPoints -> Text(
                "Pas assez de points : les yeux n'étaient pas assez visibles. Vérifiez la lumière et la distance, puis recommencez.",
                color = Color(0xFFC62828), textAlign = TextAlign.Center
            )
            else -> {
                val main = checkCm ?: errorCm
                if (main != null) Text(precisionText(main), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    "Vérification après calibration : ${checkCm?.let { "%.1f cm".format(it) } ?: "non mesurée"}   ·   " +
                    "pendant la calibration : ${errorCm?.let { "%.1f cm".format(it) } ?: "—"}",
                    color = Color.DarkGray
                )
                refinedFrom?.let { Text("Avant la retouche : %.1f cm".format(it), color = Color.Gray) }
                Text(
                    "La vérification compte : c'est la précision réelle. Le rond orange suit votre regard : regardez quelques pictos pour juger avant d'enregistrer.",
                    fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Annuler") }
            OutlinedButton(onClick = onRetry) { Text("Recommencer") }
            if (enoughPoints) OutlinedButton(onClick = onRecheck) { Text("Vérifier à nouveau") }
            Button(onClick = onSave, enabled = enoughPoints, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Enregistrer") }
        }
    }
}

/**
 * Position du regard stabilisée, en coordonnées normalisées ; null si les yeux ne sont pas visibles.
 * 1. médiane des 5 dernières mesures (un iris mal repéré sur une image est ignoré) ;
 * 2. filtre de fixation : le pointeur reste immobile tant que le regard reste dans la même zone,
 *    et ne se déplace que sur un vrai mouvement de l'œil.
 */
@Composable
fun rememberGazePoint(tracker: GazeTracker, model: GazeModel): State<Pair<Double, Double>?> {
    val median = remember(model) { MedianWindow(5) }
    val fixation = remember(model) { FixationFilter() }
    val point = remember(model) { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(model) {
        tracker.frames.filterNotNull().collect { f ->
            val feat = f.features
            if (f.usable && feat != null) {
                val (x, y) = model.predict(median.add(feat.regressors()))
                val (sx, sy) = fixation.filter(clamp01(x), clamp01(y))
                point.value = sx to sy
            } else if (!f.faceDetected) {
                point.value = null; median.reset(); fixation.reset()
            }
        }
    }
    return point
}

/** Rond semi-transparent à l'endroit regardé. */
@Composable
fun GazeCursor(point: Pair<Double, Double>?) {
    if (point == null) return
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val size = 56.dp
        val sizePx = with(LocalDensity.current) { size.toPx() }
        Box(
            modifier = Modifier
                .offset { IntOffset((constraints.maxWidth * point.first - sizePx / 2).toInt(), (constraints.maxHeight * point.second - sizePx / 2).toInt()) }
                .size(size)
                .background(Color(0x55FF6D00), CircleShape)
                .border(3.dp, Color(0xCCFF6D00), CircleShape)
        )
    }
}

/** Vérification : quelques cibles sur les pictos ; l'écart moyen entre le regard estimé et la cible est affiché en cm. */
@Composable
private fun VerificationRun(
    tracker: GazeTracker, model: GazeModel, targets: List<Pair<Double, Double>>,
    widthCm: Double, heightCm: Double,
    title: String = "Vérification",
    onResult: ((Double?) -> Unit)? = null, // Si présent : renvoie la précision mesurée dès la fin (vérification automatique)
    onClose: () -> Unit
) {
    val point by rememberGazePoint(tracker, model)
    var index by remember { mutableIntStateOf(0) }
    var resultCm by remember { mutableStateOf<Double?>(null) }
    var jitterCm by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        val errors = mutableListOf<Double>()
        val jitters = mutableListOf<Double>()
        targets.forEachIndexed { i, (tx, ty) ->
            index = i
            delay(1000)
            val seen = mutableListOf<Pair<Double, Double>>()
            repeat(15) { delay(100); point?.let { seen += it } }
            if (seen.size >= 5) {
                val mx = seen.map { it.first }.average(); val my = seen.map { it.second }.average()
                errors += kotlin.math.hypot((mx - tx) * widthCm, (my - ty) * heightCm)
                // Stabilité : distance moyenne du pointeur à sa position moyenne pendant la fixation
                jitters += seen.map { kotlin.math.hypot((it.first - mx) * widthCm, (it.second - my) * heightCm) }.average()
            }
        }
        jitterCm = jitters.takeIf { it.isNotEmpty() }?.average()
        android.util.Log.i("Nina-Regard", "Vérification : erreur %.2f cm, stabilité ± %.2f cm".format(errors.average(), jitterCm ?: -1.0))
        resultCm = errors.takeIf { it.isNotEmpty() }?.average()
        onResult?.invoke(resultCm)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFAF3E0))) {
        if (resultCm == null) {
            val (tx, ty) = targets[index]
            GazeTarget(tx, ty, collecting = false)
        }
        GazeCursor(point)
        Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (resultCm == null) "$title : regarde l'étoile ⭐  (${index + 1} / ${targets.size})" else "Sur les pictos : " + precisionText(resultCm!!),
                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
            )
            jitterCm?.let {
                Text(
                    "Stabilité du pointeur : ± %.1f cm".format(it) + if (it < 1.0) " (stable)" else if (it < 2.0) " (un peu instable)" else " (instable)",
                    fontSize = 16.sp, modifier = Modifier.background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            if (point == null) Text("Yeux non détectés", color = Color(0xFFC62828))
        }
        Button(onClick = onClose, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Terminer") }
    }
}

/**
 * Essai sur les pictos : les cases de l'écran principal (à leur vraie place), avec l'aimant et l'anneau de fixation,
 * exactement comme sur l'écran de Nina ; une case « sélectionnée » clignote en vert. Rien n'est activé pour de vrai.
 */
@Composable
private fun PictoTrial(tracker: GazeTracker, model: GazeModel, zones: List<NormRect>, dwellMs: Long, onClose: () -> Unit) {
    val point by rememberGazePoint(tracker, model)
    val magnet = remember { GazeMagnet() }
    val dwell = remember { GazeDwell(dwellMs) }
    var current by remember { mutableStateOf<Int?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var selections by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        snapshotFlow { point }.collect { p ->
            val now = android.os.SystemClock.uptimeMillis()
            current = if (p == null) null else magnet.update(zones, p.first, p.second, now)
            val (pr, fired) = dwell.update(current, now)
            progress = pr
            if (fired != null) { selected = fired; selections++ }
        }
    }
    LaunchedEffect(selections) { if (selected != null) { delay(600); selected = null } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFFFAF3E0))) {
        val w = constraints.maxWidth; val h = constraints.maxHeight
        val density = LocalDensity.current
        zones.forEachIndexed { i, z ->
            val isCurrent = i == current
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset((z.left * w).toInt(), (z.top * h).toInt()) }
                    .size(with(density) { (z.width * w).toFloat().toDp() }, with(density) { (z.height * h).toFloat().toDp() })
                    .background(if (i == selected) Color(0xFFA5D6A7) else Color.White, RoundedCornerShape(16.dp))
                    .border(if (isCurrent) 6.dp else 1.dp, if (isCurrent) Color(0xCCFF6D00) else Color.LightGray, RoundedCornerShape(16.dp))
            ) {
                if (isCurrent && progress > 0f) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(56.dp), color = Color(0xFFFF6D00), strokeWidth = 7.dp, trackColor = Color(0x33FF6D00))
                }
            }
        }
        Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Essai : fixez une case jusqu'à ce que l'anneau se remplisse ($selections sélections)",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 6.dp)
            )
            if (point == null) Text("Yeux non détectés", color = Color(0xFFC62828))
        }
        Button(onClick = onClose, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Terminer") }
    }
}
