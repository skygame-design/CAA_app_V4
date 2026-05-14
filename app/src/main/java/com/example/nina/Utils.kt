package com.example.nina

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
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
 * Recherche récursive du chemin d'un mot dans la structure des boutons.
 */
fun findWordPaths(query: String, buttons: List<AACCell>, currentPath: String = "Accueil"): List<Pair<AACCell, String>> {
    if (query.isBlank()) return emptyList()
    val results = mutableListOf<Pair<AACCell, String>>()
    
    for (cell in buttons) {
        if (cell.label.contains(query, ignoreCase = true)) {
            results.add(cell to currentPath)
        }
        if (cell.isFolder) {
            results.addAll(findWordPaths(query, cell.content, "$currentPath > ${cell.label}"))
        }
    }
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


