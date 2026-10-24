package com.example.nina

import android.content.Context
import java.io.File
import java.time.LocalDate

/** Une sauvegarde automatique : sa date, son fichier et le nombre de mots qu'elle contient. */
data class AutoBackupInfo(val date: LocalDate, val file: File, val wordCount: Int)

/**
 * Sauvegardes automatiques de la grille, dans la tablette (dossier « sauvegardes » de l'app) :
 * une par jour (la dernière version de la journée), on garde les 7 derniers jours.
 * Elles servent à récupérer la grille si les données enregistrées sont abîmées,
 * ou à revenir en arrière après une mauvaise manipulation.
 */
object AutoBackup {
    const val KEEP_DAYS = 7
    private const val PREFIX = "nina_"
    private const val SUFFIX = ".json"

    private fun dir(context: Context) = File(context.filesDir, "sauvegardes").apply { mkdirs() }

    fun fileName(date: LocalDate) = "$PREFIX$date$SUFFIX"

    /** Date d'une sauvegarde d'après son nom de fichier, ou null si ce n'est pas une sauvegarde. */
    fun dateOf(name: String): LocalDate? =
        if (name.startsWith(PREFIX) && name.endsWith(SUFFIX))
            runCatching { LocalDate.parse(name.removePrefix(PREFIX).removeSuffix(SUFFIX)) }.getOrNull()
        else null

    /** Fichiers à supprimer pour ne garder que les `keep` sauvegardes les plus récentes. */
    fun namesToDelete(names: List<String>, keep: Int = KEEP_DAYS): List<String> =
        names.filter { dateOf(it) != null }.sortedByDescending { dateOf(it) }.drop(keep)

    /** Enregistre (ou remplace) la sauvegarde du jour, puis supprime les plus anciennes. */
    fun save(context: Context, layout: NinaBackup, today: LocalDate = LocalDate.now()) {
        if (layout.buttons.isEmpty() && layout.coreWords.isNullOrEmpty()) return // Rien d'utile à garder
        val d = dir(context)
        val tmp = File(d, fileName(today) + ".tmp")
        tmp.writeText(GsonInstance.gson.toJson(layout))
        tmp.renameTo(File(d, fileName(today)).also { it.delete() }) // Écriture complète puis remplacement
        namesToDelete(d.list()?.toList().orEmpty()).forEach { File(d, it).delete() }
    }

    /** Sauvegardes lisibles, de la plus récente à la plus ancienne. */
    fun list(context: Context): List<AutoBackupInfo> =
        dir(context).listFiles().orEmpty().mapNotNull { f ->
            val date = dateOf(f.name) ?: return@mapNotNull null
            val layout = read(f) ?: return@mapNotNull null
            AutoBackupInfo(date, f, VocabularyPacks.wordCount(layout))
        }.sortedByDescending { it.date }

    fun read(file: File): NinaBackup? = runCatching { parseBackup(file.readText()) }.getOrNull()
        ?.takeIf { it.buttons.isNotEmpty() || !it.coreWords.isNullOrEmpty() }

    /** La sauvegarde la plus récente qui se lit correctement. */
    fun latest(context: Context): Pair<LocalDate, NinaBackup>? =
        list(context).firstNotNullOfOrNull { info -> read(info.file)?.let { info.date to it } }
}
