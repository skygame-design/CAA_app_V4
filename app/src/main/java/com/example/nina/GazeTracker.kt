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
    @Volatile private var running = false

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
            .setResultListener { result: FaceLandmarkerResult, image -> onResult(result, image.width, image.height) }
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
                    .setTargetResolution(android.util.Size(640, 480))
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
            val bitmap = if (rotation == 0) raw else
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
            if (frameCount++ % 15 == 0) lastBrightness = brightnessOf(bitmap)
            landmarker?.detectAsync(BitmapImageBuilder(bitmap).build(), SystemClock.uptimeMillis())
        } catch (e: Exception) {
            Log.w("Nina-Regard", "Image ignorée : ${e.message}")
        } finally {
            proxy.close()
        }
    }

    private fun onResult(result: FaceLandmarkerResult, width: Int, height: Int) {
        if (!running) return
        val face = result.faceLandmarks().firstOrNull()
        val features = face?.let { pts ->
            GazeFeatures.from(pts.map { FacePoint(it.x(), it.y()) }, aspect = width.toFloat() / height.coerceAtLeast(1))
        }
        _frames.value = GazeFrame(SystemClock.uptimeMillis(), face != null, features, lastBrightness)
        // Mesures de réglage (une fois par seconde environ), visibles avec « adb logcat -s Nina-Regard »
        if (frameCount % 30 == 0) features?.let {
            Log.d("Nina-Regard", "image ${width}x$height, taille visage=%.3f, ouverture yeux=%.2f, regard=(%.3f ; %.3f), lumière=%.2f"
                .format(it.faceSize, it.eyeOpenness, it.gazeX, it.gazeY, lastBrightness))
        }
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
    val errorCm: Double? // Erreur moyenne estimée (validation croisée), en cm sur l'écran
) {
    fun model() = GazeModel.fit(samples)
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

    fun activeProfile(context: Context): GazeProfile? {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE, null)
        val all = profiles(context)
        return all.firstOrNull { it.name == name } ?: all.firstOrNull()
    }

    private const val KEY_SELECTION = "gaze_selection_enabled"
    private const val KEY_DWELL = "gaze_dwell_ms"

    fun selectionEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SELECTION, false)
    fun setSelectionEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SELECTION, enabled).apply()

    /** Durée de fixation pour activer un picto (1,2 s par défaut, réglable de 0,6 à 3 s). */
    fun dwellMs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_DWELL, 1200L)
    fun setDwellMs(context: Context, ms: Long) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_DWELL, ms).apply()

    fun cursorEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CURSOR, false)
    fun setCursorEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CURSOR, enabled).apply()
}
