package com.example.nina

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

private enum class GazeScreen { HOME, CALIBRATION, RESULT, VERIFICATION, FREE }

private val Accent = Color(0xFF4A5D91)

/**
 * Commande oculaire (étape 1) : état de la caméra, calibration à 9 points, vérification de la précision,
 * et curseur de regard sur l'écran principal. La sélection des pictos par le regard viendra ensuite.
 */
@Composable
fun EyeGazeDialog(
    tracker: GazeTracker,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    cursorEnabled: Boolean,
    onCursorEnabledChange: (Boolean) -> Unit,
    onProfileChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(GazeScreen.HOME) }
    var profile by remember { mutableStateOf(GazeStore.activeProfile(context)) }
    var pendingSamples by remember { mutableStateOf<List<GazeSample>>(emptyList()) }
    val (widthCm, heightCm) = screenSizeCm()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        HideSystemBarsInDialog()
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF0F0F5)) {
            when (screen) {
                GazeScreen.HOME -> GazeHome(
                    tracker = tracker, profile = profile, hasCameraPermission = hasCameraPermission,
                    onRequestPermission = onRequestPermission,
                    cursorEnabled = cursorEnabled, onCursorEnabledChange = onCursorEnabledChange,
                    onCalibrate = { screen = GazeScreen.CALIBRATION },
                    onVerify = { screen = GazeScreen.VERIFICATION },
                    onFree = { screen = GazeScreen.FREE },
                    onDismiss = onDismiss
                )
                GazeScreen.CALIBRATION -> CalibrationRun(
                    tracker = tracker,
                    onFinished = { samples -> pendingSamples = samples; screen = GazeScreen.RESULT },
                    onCancel = { screen = GazeScreen.HOME }
                )
                GazeScreen.RESULT -> {
                    val error = remember(pendingSamples) { GazeModel.leaveOneOutError(pendingSamples, widthScale = widthCm, heightScale = heightCm) }
                    CalibrationResult(
                        measured = pendingSamples.size, total = GazeTargets.calibration9.size, errorCm = error,
                        onSave = {
                            val saved = GazeProfile("Principal", pendingSamples, System.currentTimeMillis(), error)
                            GazeStore.saveProfile(context, saved)
                            profile = saved
                            onProfileChanged()
                            screen = GazeScreen.HOME
                        },
                        onRetry = { screen = GazeScreen.CALIBRATION },
                        onCancel = { screen = GazeScreen.HOME }
                    )
                }
                GazeScreen.VERIFICATION, GazeScreen.FREE -> {
                    val model = remember(profile) { profile?.model() }
                    if (model == null) screen = GazeScreen.HOME
                    else VerificationRun(
                        tracker = tracker, model = model, free = screen == GazeScreen.FREE,
                        widthCm = widthCm, heightCm = heightCm,
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
    cursorEnabled: Boolean, onCursorEnabledChange: (Boolean) -> Unit,
    onCalibrate: () -> Unit, onVerify: () -> Unit, onFree: () -> Unit, onDismiss: () -> Unit
) {
    val frame by tracker.frames.collectAsState()
    val error by tracker.error.collectAsState()

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
                        Text("Profil « ${profile.name} », ${profile.samples.size} points")
                        Text("Faite le ${DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(profile.updatedAt))}", fontSize = 13.sp, color = Color.Gray)
                        profile.errorCm?.let { Text(precisionText(it), fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onCalibrate, enabled = hasCameraPermission && error == null, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                Text(if (profile == null) "Calibrer (9 points)" else "Refaire la calibration (9 points)")
            }
            OutlinedButton(onClick = onVerify, enabled = profile != null && hasCameraPermission) { Text("Vérifier la précision") }
            OutlinedButton(onClick = onFree, enabled = profile != null && hasCameraPermission) { Text("Essai libre (grille)") }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Afficher le curseur du regard sur l'écran principal", fontWeight = FontWeight.Bold)
                Text("Étape 1 : le curseur montre où l'app pense que l'enfant regarde, sans encore sélectionner les pictos.", fontSize = 13.sp, color = Color.Gray)
            }
            Switch(checked = cursorEnabled, enabled = profile != null && hasCameraPermission, onCheckedChange = onCursorEnabledChange)
        }
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
        val size = 110.dp
        val sizePx = with(LocalDensity.current) { size.toPx() }
        val left = (constraints.maxWidth * x - sizePx / 2).toInt()
        val top = (constraints.maxHeight * y - sizePx / 2).toInt()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.offset { IntOffset(left, top) }.size(size).scale(if (collecting) 0.8f else pulse)
                .background(if (collecting) Color(0xFFFFD54F) else Color(0xFFFF8A65), CircleShape)
                .border(6.dp, Color.White, CircleShape)
        ) {
            Text("⭐", fontSize = 44.sp)
        }
    }
}

/** Calibration : 9 cibles l'une après l'autre ; pour chacune, médiane des images où les yeux sont visibles. */
@Composable
private fun CalibrationRun(tracker: GazeTracker, onFinished: (List<GazeSample>) -> Unit, onCancel: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var collecting by remember { mutableStateOf(false) }
    var waitingForFace by remember { mutableStateOf(false) }
    val frame by tracker.frames.collectAsState()

    LaunchedEffect(Unit) {
        val samples = mutableListOf<GazeSample>()
        GazeTargets.calibration9.forEachIndexed { i, (tx, ty) ->
            index = i; collecting = false
            delay(1000) // Le temps que le regard arrive sur la cible
            collecting = true
            val frames = mutableListOf<DoubleArray>()
            // Jusqu'à 30 images utilisables, au plus 3 secondes (clignements ignorés)
            withTimeoutOrNull(3000) {
                tracker.frames.filterNotNull().first { f ->
                    waitingForFace = !f.usable
                    if (f.usable) f.features?.let { frames += it.regressors() }
                    frames.size >= 30 // Arrête la collecte de cette cible
                }
            }
            if (frames.size >= 8) medianFeatures(frames)?.let { samples += GazeSample(it, tx, ty) }
        }
        onFinished(samples)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF263238))) {
        val (tx, ty) = GazeTargets.calibration9[index]
        GazeTarget(tx, ty, collecting)
        Text(
            if (waitingForFace && collecting) "Je ne vois pas les yeux… regarde l'étoile !" else "Regarde l'étoile ⭐  (${index + 1} / ${GazeTargets.calibration9.size})",
            color = Color.White, fontSize = 20.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
        )
        if (frame?.faceDetected == false) Text("Aucun visage détecté", color = Color(0xFFFFAB91), modifier = Modifier.align(Alignment.Center))
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text("Annuler", color = Color.White) }
    }
}

@Composable
private fun CalibrationResult(measured: Int, total: Int, errorCm: Double?, onSave: () -> Unit, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Calibration terminée", fontWeight = FontWeight.Bold, fontSize = 28.sp)
        Text("$measured points mesurés sur $total", fontSize = 18.sp)
        when {
            measured < 6 -> Text(
                "Pas assez de points : les yeux n'étaient pas assez visibles. Vérifiez la lumière et la distance, puis recommencez.",
                color = Color(0xFFC62828), textAlign = TextAlign.Center
            )
            errorCm != null -> Text(precisionText(errorCm), fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Annuler") }
            OutlinedButton(onClick = onRetry) { Text("Recommencer") }
            Button(onClick = onSave, enabled = measured >= 6, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Enregistrer") }
        }
    }
}

/** Position du regard lissée, en coordonnées normalisées ; null si les yeux ne sont pas visibles. */
@Composable
fun rememberGazePoint(tracker: GazeTracker, model: GazeModel): State<Pair<Double, Double>?> {
    val fx = remember(model) { OneEuroFilter() }
    val fy = remember(model) { OneEuroFilter() }
    val point = remember(model) { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(model) {
        tracker.frames.filterNotNull().collect { f ->
            val feat = f.features
            if (f.usable && feat != null) {
                val (x, y) = model.predict(feat.regressors())
                point.value = clamp01(fx.filter(x, f.timeMs)) to clamp01(fy.filter(y, f.timeMs))
            } else if (!f.faceDetected) {
                point.value = null; fx.reset(); fy.reset()
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

/**
 * Vérification : 5 cibles, l'écart moyen entre le regard estimé et la cible est affiché en cm.
 * Mode libre : une grille de 4 × 3 cases s'allume là où l'enfant regarde (comme les pictos à 4 par rangée).
 */
@Composable
private fun VerificationRun(tracker: GazeTracker, model: GazeModel, free: Boolean, widthCm: Double, heightCm: Double, onClose: () -> Unit) {
    val point by rememberGazePoint(tracker, model)
    var index by remember { mutableIntStateOf(0) }
    var resultCm by remember { mutableStateOf<Double?>(null) }

    if (!free) LaunchedEffect(Unit) {
        val errors = mutableListOf<Double>()
        GazeTargets.verification5.forEachIndexed { i, (tx, ty) ->
            index = i
            delay(1000)
            val seen = mutableListOf<Pair<Double, Double>>()
            repeat(15) { delay(100); point?.let { seen += it } }
            if (seen.size >= 5) {
                val mx = seen.map { it.first }.average(); val my = seen.map { it.second }.average()
                errors += kotlin.math.hypot((mx - tx) * widthCm, (my - ty) * heightCm)
            }
        }
        resultCm = errors.takeIf { it.isNotEmpty() }?.average()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFAF3E0))) {
        if (free) {
            // Grille 4 × 3 : la case regardée s'allume
            Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { row ->
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) { col ->
                            val lit = point?.let { (it.first * 4).toInt().coerceIn(0, 3) == col && (it.second * 3).toInt().coerceIn(0, 2) == row } == true
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if (lit) Color(0xFFC8E6C9) else Color.White, RoundedCornerShape(16.dp))
                                .border(if (lit) 4.dp else 1.dp, if (lit) Color(0xFF43A047) else Color.LightGray, RoundedCornerShape(16.dp)))
                        }
                    }
                }
            }
        } else if (resultCm == null) {
            val (tx, ty) = GazeTargets.verification5[index]
            GazeTarget(tx, ty, collecting = false)
        }
        GazeCursor(point)

        Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    free -> "Essai libre : la case regardée s'allume en vert"
                    resultCm == null -> "Regarde l'étoile ⭐  (${index + 1} / ${GazeTargets.verification5.size})"
                    else -> precisionText(resultCm!!)
                },
                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (point == null) Text("Yeux non détectés", color = Color(0xFFC62828))
        }
        Button(onClick = onClose, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Terminer") }
    }
}
