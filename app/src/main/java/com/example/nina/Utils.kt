package com.example.nina

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.edit



/**
 * Singleton pour l'instance GSON.
 */
object GsonInstance {
    val gson = Gson()
}

/**
 * Plein écran « immersif » : cache la barre d'état (heure, batterie) et la barre de navigation.
 * Un glissement depuis le bord de l'écran les fait réapparaître quelques secondes, par-dessus l'app,
 * sans déplacer les pictos ; elles se recachent ensuite seules.
 * À appeler pour l'activité et pour chaque fenêtre de dialogue (sinon les barres reviennent quand elle s'ouvre).
 */
fun hideSystemBars(window: android.view.Window) {
    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * Sauvegarde une image depuis un URI vers le stockage interne.
 */
fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val resolver = context.contentResolver
        // Taille d'origine (une photo d'appareil fait souvent 4000 × 3000) : on la réduit dès le décodage
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = photoSampleSize(bounds.outWidth, bounds.outHeight, PHOTO_MAX_SIZE) }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        // Photo prise tablette tournée : l'orientation est dans les données EXIF, on redresse l'image
        val rotation = resolver.openInputStream(uri)?.use { exifRotation(android.media.ExifInterface(it)) } ?: 0
        val scale = minOf(1f, PHOTO_MAX_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height))
        if (rotation != 0 || scale < 1f) {
            val matrix = android.graphics.Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        // Stockée dans la tablette (dossier privé de l'app) : la photo reste visible sans réseau
        val file = File(context.filesDir, "photo_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        file.absolutePath
    } catch (_: Exception) {
        null
    }
}

/** Plus grand côté d'une photo enregistrée : net sur un picto, léger dans la tablette et les sauvegardes. */
const val PHOTO_MAX_SIZE = 1024

/** Facteur de réduction au décodage (puissance de 2) pour que le plus grand côté reste au moins égal à maxSize. */
fun photoSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxSize) sample *= 2
    return sample
}

private fun exifRotation(exif: android.media.ExifInterface): Int =
    when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

/**
 * Télécharge une image depuis le web et la stocke dans la tablette, pour qu'elle reste visible hors réseau.
 * Le fichier est gardé tel quel (PNG, JPG ou SVG) : le SVG ne peut pas passer par BitmapFactory.
 * Renvoie le chemin local, ou null si le téléchargement échoue.
 */
fun saveImageLocally(context: Context, urlString: String, name: String): String? {
    return try {
        val connection = URL(urlString).openConnection().apply {
            connectTimeout = 10_000; readTimeout = 15_000
        }
        val bytes = connection.getInputStream().use { it.readBytes() }
        if (bytes.isEmpty()) return null
        val safeName = name.replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
        val file = File(context.filesDir, "$safeName.${imageExtension(urlString, connection.contentType, bytes)}")
        file.writeBytes(bytes)
        file.absolutePath
    } catch (_: Exception) {
        null
    }
}

/** Extension du fichier image, d'après le type annoncé, le contenu, puis l'adresse. */
fun imageExtension(url: String, contentType: String?, bytes: ByteArray): String {
    val head = String(bytes.copyOfRange(0, minOf(bytes.size, 256)), Charsets.UTF_8)
    return when {
        contentType?.contains("svg") == true || head.contains("<svg") -> "svg"
        contentType?.contains("jpeg") == true || url.lowercase().substringBefore('?').endsWith(".jpg") -> "jpg"
        else -> "png"
    }
}

/** Vrai si l'image est une adresse web (donc pas encore enregistrée dans la tablette). */
fun isRemoteImage(imageUrl: String?) = imageUrl != null && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))

/**
 * Contenu complet d'un fichier de sauvegarde : grille, Core Words et Quick Fires.
 * coreWords et quickFires sont null pour les anciennes sauvegardes (grille seule).
 */
data class NinaBackup(
    val buttons: List<AACCell>,
    val coreWords: List<AACCell>? = null,
    val quickFires: List<AACCell>? = null,
    // Photos et images de la galerie (nom du fichier -> contenu en base64) : elles n'existent que dans la tablette,
    // le fichier partagé les emporte pour qu'une restauration sur une autre tablette les retrouve
    val photos: Map<String, String>? = null
)

/** Tous les pictos d'une sauvegarde, dossiers compris. */
fun NinaBackup.allCells(): List<AACCell> {
    fun walk(cells: List<AACCell>): List<AACCell> = cells.flatMap { listOf(it) + walk(it.content) }
    return walk(buttons) + walk(coreWords.orEmpty()) + walk(quickFires.orEmpty())
}

/** Ajoute à la sauvegarde les images enregistrées dans la tablette qui n'ont pas d'adresse web d'origine. */
fun NinaBackup.withEmbeddedPhotos(context: Context): NinaBackup {
    val photos = allCells()
        .filter { it.sourceUrl == null && it.imageUrl != null && !isRemoteImage(it.imageUrl) }
        .mapNotNull { cell -> File(cell.imageUrl!!).takeIf { it.isFile && it.parentFile == context.filesDir } }
        .distinctBy { it.name }
        .associate { it.name to android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP) }
    return if (photos.isEmpty()) this else copy(photos = photos)
}

/**
 * Restauration : réécrit dans la tablette les photos emportées par la sauvegarde,
 * et fait pointer les pictos vers ces fichiers (le dossier de l'app peut différer d'une tablette à l'autre).
 */
fun NinaBackup.installEmbeddedPhotos(context: Context): NinaBackup {
    photos.orEmpty().forEach { (name, data) ->
        val safe = File(name).name // Jamais en dehors du dossier de l'app
        val file = File(context.filesDir, safe)
        if (!file.exists()) runCatching { file.writeBytes(android.util.Base64.decode(data, android.util.Base64.DEFAULT)) }
    }
    fun relink(cells: List<AACCell>): List<AACCell> = cells.map { cell ->
        val local = cell.imageUrl?.takeIf { !isRemoteImage(it) }?.let { File(it) }
        val moved = local?.takeIf { !it.exists() }?.let { File(context.filesDir, it.name) }?.takeIf { it.exists() }
        val content = androidx.compose.runtime.mutableStateListOf<AACCell>().apply { addAll(relink(cell.content)) }
        cell.copy(imageUrl = moved?.absolutePath ?: cell.imageUrl, content = content)
    }
    return copy(buttons = relink(buttons), coreWords = coreWords?.let { relink(it) }, quickFires = quickFires?.let { relink(it) }, photos = null)
}

/**
 * Lit un fichier de sauvegarde. Accepte le nouveau format (objet NinaBackup)
 * et l'ancien format (simple liste des boutons de la grille).
 */
fun parseBackup(json: String): NinaBackup {
    val root = JsonParser.parseString(json)
    val backup = if (root.isJsonArray) {
        val buttons: List<AACCell> = GsonInstance.gson.fromJson(root, object : TypeToken<List<AACCell>>() {}.type)
        NinaBackup(buttons)
    } else {
        GsonInstance.gson.fromJson(root, NinaBackup::class.java)
    }
    return NinaBackup(
        buttons = normalizeCells(backup.buttons),
        coreWords = backup.coreWords?.let { normalizeCells(it) },
        quickFires = backup.quickFires?.let { normalizeCells(it) },
        photos = backup.photos
    )
}

/**
 * Gère les statistiques d'utilisation des mots.
 */
fun loadStats(context: Context): Map<String, Int> {
    val json = context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).getString("usage_stats", null)
    return if (json != null) {
        GsonInstance.gson.fromJson(json, object : TypeToken<Map<String, Int>>() {}.type)
    } else emptyMap()
}

fun saveStats(context: Context, stats: Map<String, Int>) {
    context.getSharedPreferences("nina_prefs", Context.MODE_PRIVATE).edit {
        putString("usage_stats", GsonInstance.gson.toJson(stats))
    }
}

/**
 * Un mot trouvé et le chemin pour l'atteindre.
 * steps : les cases à toucher dans l'ordre (dossiers puis le mot), depuis l'accueil.
 * place : description lisible ("Accueil > Nourriture", "Colonne de gauche"...).
 */
data class WordGuide(val cell: AACCell, val steps: List<AACCell>, val place: String)

/**
 * Recherche un mot dans la grille, dans la colonne de gauche (y compris dans ses dossiers) et dans les phrases rapides.
 * Les pictos masqués, et le contenu des dossiers masqués, sont ignorés : on ne peut pas les montrer.
 */
fun findWordGuides(query: String, buttons: List<AACCell>, coreWords: List<AACCell>, quickFires: List<AACCell>): List<WordGuide> {
    if (query.isBlank()) return emptyList()
    val results = mutableListOf<WordGuide>()

    // folders : les dossiers à toucher avant d'arriver au mot ; root : nom de la zone de départ
    fun search(cells: List<AACCell>, folders: List<AACCell>, root: String) {
        for (cell in cells) {
            if (cell.isHidden) continue
            if (cell.label.contains(query, ignoreCase = true)) {
                val place = (listOf(root) + folders.map { it.label }).joinToString(" > ")
                results += WordGuide(cell, folders + cell, place)
            }
            if (cell.isFolder) search(cell.content, folders + cell, root)
        }
    }

    search(buttons, emptyList(), "Accueil")
    search(coreWords, emptyList(), "Colonne de gauche")
    quickFires.filter { !it.isHidden && it.label.contains(query, ignoreCase = true) }
        .forEach { results += WordGuide(it, listOf(it), "Phrases rapides") }
    return results
}

/** Tous les dossiers (grille et colonne de gauche, sous-dossiers compris), pour « Déplacer vers ». */
fun allFolders(vararg lists: List<AACCell>): List<AACCell> {
    val result = mutableListOf<AACCell>()
    fun collect(cells: List<AACCell>) {
        cells.forEach { if (it.isFolder) { result += it; collect(it.content) } }
    }
    lists.forEach { collect(it) }
    return result
}

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null

    fun startRecording(fileName: String): String {
        val file = File(context.filesDir, "audio_${fileName}_${System.currentTimeMillis()}.mp4")
        mediaRecorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        return file.absolutePath
    }

    fun stopRecording() {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }
}



/** Volume multimédia (celui de la voix et des sons enregistrés) à zéro ou coupé. */
fun isMediaMuted(context: Context): Boolean {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    val stream = android.media.AudioManager.STREAM_MUSIC
    return audio.getStreamVolume(stream) == 0 || audio.isStreamMute(stream)
}

/** Suit en direct le volume multimédia (vérifié toutes les secondes, sans permission particulière). */
@androidx.compose.runtime.Composable
fun rememberMediaMuted(): androidx.compose.runtime.State<Boolean> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val muted = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(isMediaMuted(context)) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            muted.value = isMediaMuted(context)
            kotlinx.coroutines.delay(1000)
        }
    }
    return muted
}

/** Affiche le réglage du volume multimédia d'Android (sans le modifier). */
fun showVolumePanel(context: Context) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_SAME, android.media.AudioManager.FLAG_SHOW_UI)
}
