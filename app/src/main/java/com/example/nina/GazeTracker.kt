package com.example.nina

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import kotlin.math.hypot

/** Ce que la caméra voit à un instant donné. */
data class GazeFrame(
    val timeMs: Long,
    val faceDetected: Boolean,
    val features: GazeFeatures?,
    val brightness: Double // Luminosité moyenne de l'image (0..1), pour prévenir si la pièce est trop sombre
) {
    val eyesOpen get() = features != null && features.eyeOpenness > GazeFeatures.CLOSED_EYE
    val usable get() = faceDetected && eyesOpen
}

/**
 * Suivi du regard par la caméra frontale : CameraX fournit les images, MediaPipe Face Landmarker
 * y repère le visage et les iris. Tout se fait dans la tablette ; aucune image n'est enregistrée.
 * La caméra ne tourne que entre start() et stop() (économie de batterie).
 */
class GazeTracker(private val context: Context) {
    private val _frames = MutableStateFlow<GazeFrame?>(null)
    val frames: StateFlow<GazeFrame?> = _frames

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var landmarker: FaceLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var lastBrightness = 0.5
    private var frameCount = 0
    private var lastFpsTime = 0L
    private var resultCount = 0
    @Volatile private var running = false

    /**
     * Repérage fin de l'iris dans l'image en haute définition (sinon : iris de MediaPipe seul).
     * Les deux modes donnent des mesures différentes : chacun a sa propre calibration.
     */
    @Volatile var fineIris = GazeStore.fineIris(context)

    // Images en pleine définition gardées le temps que MediaPipe renvoie son résultat (retrouvées par leur horodatage)
    private val fullFrames = java.util.concurrent.ConcurrentHashMap<Long, FullFrame>()

    /** Image caméra telle quelle (non tournée : la tourner en entier coûte cher) et sa rotation. */
    private class FullFrame(val bitmap: Bitmap, val rotation: Int) {
        val rotatedWidth get() = if (rotation % 180 == 0) bitmap.width else bitmap.height
        val rotatedHeight get() = if (rotation % 180 == 0) bitmap.height else bitmap.width
    }
    private var refinedCount = 0
    private var refineTried = 0

    fun start(lifecycleOwner: LifecycleOwner) {
        if (running) return
        running = true
        _error.value = null
        executor.execute {
            try {
                if (landmarker == null) landmarker = createLandmarker()
            } catch (e: Exception) {
                Log.e("Nina-Regard", "Détection du visage impossible", e)
                _error.value = "La détection du visage n'a pas pu démarrer."
                running = false
                return@execute
            }
            ContextCompat.getMainExecutor(context).execute { bindCamera(lifecycleOwner) }
        }
    }

    fun stop() {
        running = false
        ContextCompat.getMainExecutor(context).execute { cameraProvider?.unbindAll() }
        _frames.value = null
    }

    private fun createLandmarker(): FaceLandmarker {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputFacialTransformationMatrixes(true) // Pose 3D de la tête
            .setResultListener { result: FaceLandmarkerResult, image -> onResult(result, image.width, image.height, result.timestampMs()) }
            .setErrorListener { e -> Log.e("Nina-Regard", "Erreur MediaPipe", e) }
            .build()
        return FaceLandmarker.createFromOptions(context, options)
    }

    private fun bindCamera(lifecycleOwner: LifecycleOwner) {
        if (!running) return
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                @Suppress("DEPRECATION")
                val analysis = ImageAnalysis.Builder()
                    // Haute définition pour le repérage fin de l'iris (l'œil fait ~90 pixels de large au lieu de ~30 en 640 × 480) ;
                    // MediaPipe, lui, reçoit une image réduite (il ramène de toute façon le visage à 256 × 256)
                    .setTargetResolution(android.util.Size(1920, 1440))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(executor) { proxy -> analyze(proxy) }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                Log.e("Nina-Regard", "Caméra frontale indisponible", e)
                _error.value = "La caméra frontale n'est pas disponible."
                running = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            if (!running) return
            val raw = proxy.toBitmap()
            val rotation = proxy.imageInfo.rotationDegrees
            // MediaPipe sur une image réduite et tournée, en une seule opération ; l'image pleine définition
            // reste non tournée : seuls les deux petits carrés autour des yeux y seront lus
            val scale = if (maxOf(raw.width, raw.height) > 1300) 0.5f else 1f
            val small = if (rotation == 0 && scale == 1f) raw else Bitmap.createBitmap(
                raw, 0, 0, raw.width, raw.height, Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }, true
            )
            if (frameCount++ % 15 == 0) lastBrightness = brightnessOf(small)
            val time = SystemClock.uptimeMillis()
            if (fineIris && scale < 1f) {
                fullFrames[time] = FullFrame(raw, rotation)
                fullFrames.keys.filter { it < time - 1000 }.forEach { fullFrames.remove(it) }
            }
            landmarker?.detectAsync(BitmapImageBuilder(small).build(), time)
        } catch (e: Exception) {
            Log.w("Nina-Regard", "Image ignorée : ${e.message}")
        } finally {
            proxy.close()
        }
    }

    private fun onResult(result: FaceLandmarkerResult, width: Int, height: Int, timestamp: Long) {
        if (!running) return
        val face = result.faceLandmarks().firstOrNull()
        val full = fullFrames.remove(timestamp)
        val features = face?.let { pts ->
            var points = pts.map { FacePoint(it.x(), it.y()) }
            if (fineIris && full != null) points = refineIrises(points, full)
            val pose = result.facialTransformationMatrixes().orElse(null)?.firstOrNull()?.let { HeadCompensation.anglesFromMatrix(it) }
            GazeFeatures.from(points, aspect = width.toFloat() / height.coerceAtLeast(1))
                ?.let { f -> if (pose == null) f else f.copy(yaw = pose.first, pitch = pose.second) }
        }
        _frames.value = GazeFrame(SystemClock.uptimeMillis(), face != null, features, lastBrightness)
        // Mesures de réglage toutes les 30 images analysées (compteur propre à ce fil de résultats)
        if (++resultCount % 30 == 0) {
            val now = SystemClock.uptimeMillis()
            if (lastFpsTime > 0) Log.d("Nina-Regard", "%.1f images/s".format(30_000.0 / (now - lastFpsTime)))
            lastFpsTime = now
        }
        if (resultCount % 30 == 0) features?.let {
            Log.d("Nina-Regard", "image ${width}x$height, taille visage=%.3f, ouverture yeux=%.2f, regard=(%.3f ; %.3f), lumière=%.2f, iris affinés %d/%d, tête %.1f° / %.1f°"
                .format(it.faceSize, it.eyeOpenness, it.gazeX, it.gazeY, lastBrightness, refinedCount, refineTried,
                    Math.toDegrees(it.yaw), Math.toDegrees(it.pitch)))
            refinedCount = 0; refineTried = 0
        }
    }

    /**
     * Affine les deux centres d'iris (points 468 et 473) dans l'image pleine définition.
     * Rayon de l'iris : points 469–472 et 474–477 ; paupières : 159/145 et 386/374.
     */
    private fun refineIrises(points: List<FacePoint>, full: FullFrame): List<FacePoint> {
        if (points.size < 478) return points
        val out = points.toMutableList()
        // Calculs dans le repère de l'image tournée (celui de MediaPipe), pixels lus dans l'image d'origine
        val fw = full.rotatedWidth; val fh = full.rotatedHeight
        val src = full.bitmap
        listOf(Triple(468, 469..472, 159 to 145), Triple(473, 474..477, 386 to 374)).forEach { (center, ring, lids) ->
            refineTried++
            val cx = points[center].x.toDouble() * fw; val cy = points[center].y.toDouble() * fh
            val radius = ring.map { hypot(points[it].x.toDouble() * fw - cx, points[it].y.toDouble() * fh - cy) }.average()
            val half = (radius * 1.6).toInt().coerceAtLeast(4)
            val left = (cx - half).toInt().coerceIn(0, fw - 1); val top = (cy - half).toInt().coerceIn(0, fh - 1)
            val w = (2 * half).coerceAtMost(fw - left); val h = (2 * half).coerceAtMost(fh - top)
            if (w < 8 || h < 8) return@forEach
            // Le carré correspondant dans l'image d'origine
            val (ax, ay) = rotatedToSource(left, top, src.width, src.height, full.rotation)
            val (bx, by) = rotatedToSource(left + w - 1, top + h - 1, src.width, src.height, full.rotation)
            val sl = minOf(ax, bx); val st = minOf(ay, by); val sw = maxOf(ax, bx) - sl + 1; val sh = maxOf(ay, by) - st + 1
            val argb = IntArray(sw * sh)
            src.getPixels(argb, 0, sw, sl, st, sw, sh)
            val gray = IntArray(w * h) { i ->
                val (x, y) = rotatedToSource(left + i % w, top + i / w, src.width, src.height, full.rotation)
                val c = argb[(y - st) * sw + (x - sl)]
                (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)).toInt()
            }
            val lidTop = points[lids.first].y.toDouble() * fh - top; val lidBottom = points[lids.second].y.toDouble() * fh - top
            darkCentroid(gray, w, h, cx - left, cy - top, radius, minOf(lidTop, lidBottom), maxOf(lidTop, lidBottom))?.let { (rx, ry) ->
                out[center] = FacePoint(((rx + left) / fw).toFloat(), ((ry + top) / fh).toFloat())
                refinedCount++
            }
        }
        return out
    }

    /** Luminosité moyenne sur une grille de 16 × 16 points (rapide). */
    private fun brightnessOf(b: Bitmap): Double {
        var sum = 0.0
        val n = 16
        for (i in 0 until n) for (j in 0 until n) {
            val c = b.getPixel(b.width * (2 * i + 1) / (2 * n), b.height * (2 * j + 1) / (2 * n))
            sum += (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)) / 255.0
        }
        return sum / (n * n)
    }

    fun close() {
        stop()
        executor.execute { landmarker?.close(); landmarker = null }
    }
}

/**
 * Un profil de calibration (par exemple « Table du salon ») : les points mesurés et la date.
 * Les points bruts sont gardés pour pouvoir compléter la calibration plus tard sans tout refaire.
 */
data class GazeProfile(
    val name: String,
    val samples: List<GazeSample>,
    val updatedAt: Long,
    val errorCm: Double?, // Erreur moyenne estimée (validation croisée), en cm sur l'écran
    val checkCm: Double? = null, // Erreur mesurée par la vérification juste après la calibration (plus réaliste)
    val version: Int = 0, // GazeFeatures.FEATURE_VERSION au moment de la calibration
    val refFaceSize: Double? = null, // Taille du visage pendant la calibration (distance de référence)
    val offsetX: Double = 0.0, val offsetY: Double = 0.0, // Décalage mesuré par le dernier recentrage
    val headCompensation: HeadCompensation? = null // Effet de la rotation de la tête, mesuré à la calibration
) {
    fun model() = GazeModel.fit(samples, compensation = headCompensation)
    fun correction() = GazeCorrection(refFaceSize, offsetX, offsetY)
}

/** Enregistrement des profils de calibration et des réglages de la commande oculaire. */
object GazeStore {
    private const val PREFS = "nina_prefs"
    private const val KEY_PROFILES = "gaze_profiles"
    private const val KEY_ACTIVE = "gaze_active_profile"
    private const val KEY_CURSOR = "gaze_cursor_enabled"

    fun profiles(context: Context): List<GazeProfile> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            GsonInstance.gson.fromJson<List<GazeProfile>>(json, object : com.google.gson.reflect.TypeToken<List<GazeProfile>>() {}.type)
        }.getOrNull().orEmpty()
    }

    fun saveProfile(context: Context, profile: GazeProfile) {
        val others = profiles(context).filter { it.name != profile.name }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PROFILES, GsonInstance.gson.toJson(others + profile))
            .putString(KEY_ACTIVE, profile.name)
            .apply()
    }

    /** Nom du profil selon le mode de repérage de l'iris : les mesures diffèrent, chaque mode a sa calibration. */
    fun profileName(fineIris: Boolean) = if (fineIris) "Iris haute définition" else "Principal"

    fun activeProfile(context: Context): GazeProfile? {
        // Une calibration faite avec d'anciennes caractéristiques n'est plus utilisable : il faut la refaire
        val name = profileName(fineIris(context))
        return profiles(context).firstOrNull { it.name == name && it.version == GazeFeatures.FEATURE_VERSION }
    }

    private const val KEY_FINE_IRIS = "gaze_fine_iris"
    fun fineIris(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FINE_IRIS, true)
    fun setFineIris(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_FINE_IRIS, enabled).apply()

    private const val KEY_SELECTION = "gaze_selection_enabled"
    private const val KEY_DWELL = "gaze_dwell_ms"

    fun selectionEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SELECTION, false)
    fun setSelectionEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SELECTION, enabled).apply()

    /** Durée de fixation pour activer un picto (1,2 s par défaut, réglable de 0,6 à 3 s). */
    fun dwellMs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_DWELL, 1200L)
    fun setDwellMs(context: Context, ms: Long) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_DWELL, ms).apply()

    /** Interrupteur général : désactivée, la caméra reste éteinte, réglages et calibration sont gardés. */
    private const val KEY_ENABLED = "gaze_enabled"
    fun gazeEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, true)
    fun setGazeEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    private const val KEY_GRID = "gaze_grid"
    private const val KEY_GRID_COLUMNS = "gaze_grid_columns"
    private const val KEY_TWO_STEP = "gaze_two_step"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Grille adaptée au regard : moins de pictos par rangée et de grands espaces entre les cases. */
    fun gazeGrid(context: Context) = prefs(context).getBoolean(KEY_GRID, true)
    fun setGazeGrid(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_GRID, enabled).apply()
    fun gazeGridColumns(context: Context) = prefs(context).getInt(KEY_GRID_COLUMNS, 4)
    fun setGazeGridColumns(context: Context, columns: Int) = prefs(context).edit().putInt(KEY_GRID_COLUMNS, columns).apply()

    /** Sélection en deux temps : fixer une zone l'agrandit, puis fixer le picto agrandi l'active. */
    fun twoStep(context: Context) = prefs(context).getBoolean(KEY_TWO_STEP, false)
    fun setTwoStep(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_TWO_STEP, enabled).apply()

    fun cursorEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CURSOR, false)
    fun setCursorEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CURSOR, enabled).apply()
}
