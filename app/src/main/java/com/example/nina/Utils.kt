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
 * Sauvegarde une image depuis un URI vers le stockage interne.
 */
fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val fileName = "img_${System.currentTimeMillis()}.png"
        val file = File(context.filesDir, fileName)
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush(); out.close()
        file.absolutePath
    } catch (_: Exception) {
        null
    }
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
    val quickFires: List<AACCell>? = null
)

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
        quickFires = backup.quickFires?.let { normalizeCells(it) }
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


