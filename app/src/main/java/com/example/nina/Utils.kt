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
 * Télécharge une image depuis le web et la stocke localement.
 */
fun saveImageLocally(context: Context, urlString: String, name: String): String? {
    return try {
        val url = URL(urlString)
        val connection = url.openConnection()
        connection.connect()
        val input = connection.getInputStream()
        val bitmap = BitmapFactory.decodeStream(input)
        val file = File(context.filesDir, "$name.png")
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush(); out.close()
        file.absolutePath
    } catch (_: Exception) {
        null
    }
}

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
 * Recherche un mot dans la grille (y compris dans les dossiers) et dans les deux colonnes.
 * Les pictos masqués, et le contenu des dossiers masqués, sont ignorés : on ne peut pas les montrer.
 */
fun findWordGuides(query: String, buttons: List<AACCell>, coreWords: List<AACCell>, quickFires: List<AACCell>): List<WordGuide> {
    if (query.isBlank()) return emptyList()
    val results = mutableListOf<WordGuide>()

    fun searchGrid(cells: List<AACCell>, folders: List<AACCell>) {
        for (cell in cells) {
            if (cell.isHidden) continue
            if (cell.label.contains(query, ignoreCase = true)) {
                val place = (listOf("Accueil") + folders.map { it.label }).joinToString(" > ")
                results += WordGuide(cell, folders + cell, place)
            }
            if (cell.isFolder) searchGrid(cell.content, folders + cell)
        }
    }

    searchGrid(buttons, emptyList())
    coreWords.filter { !it.isHidden && it.label.contains(query, ignoreCase = true) }
        .forEach { results += WordGuide(it, listOf(it), "Colonne de gauche") }
    quickFires.filter { !it.isHidden && it.label.contains(query, ignoreCase = true) }
        .forEach { results += WordGuide(it, listOf(it), "Phrases rapides") }
    return results
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


