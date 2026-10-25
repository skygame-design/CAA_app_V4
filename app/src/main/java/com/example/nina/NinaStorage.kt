package com.example.nina

import android.content.Context
import android.util.Log
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Enregistrement des grilles dans la tablette (SharedPreferences « nina_prefs ») :
 * grille centrale, colonne de gauche (dossiers), phrases rapides.
 * Chargement au démarrage avec récupération depuis la sauvegarde automatique si les données sont abîmées.
 */
class NinaStorage(private val context: Context) {
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveButtons(list: List<AACCell>) = saveList(KEY_BUTTONS, list)
    fun saveCoreWords(list: List<AACCell>) = saveList(KEY_CORE, list)
    fun saveQuickFires(list: List<AACCell>) = saveList(KEY_QUICK, list)

    /** Enregistre les trois zones d'un coup. */
    fun saveAll(buttons: List<AACCell>, coreWords: List<AACCell>, quickFires: List<AACCell>) {
        saveButtons(buttons); saveCoreWords(coreWords); saveQuickFires(quickFires)
    }

    private fun saveList(key: String, list: List<AACCell>) {
        prefs.edit().putString(key, GsonInstance.gson.toJson(list)).apply()
    }

    /** Résultat du chargement au démarrage : la grille, et la date de la sauvegarde utilisée si on a dû récupérer. */
    class LoadResult(val layout: NinaBackup, val recoveredFrom: java.time.LocalDate?, val lost: Boolean)

    /** Lit une liste de pictos enregistrée ; null si elle est absente, une exception si elle est abîmée. */
    private fun readSavedList(key: String): List<AACCell>? {
        val json = prefs.getString(key, null) ?: return null
        return normalizeCells(GsonInstance.gson.fromJson<List<AACCell>>(json, object : TypeToken<List<AACCell>>() {}.type))
    }

    /**
     * Charge la grille enregistrée. Premier lancement : la grille de départ.
     * Données illisibles (fichier abîmé) : la dernière sauvegarde automatique, au lieu de tout réinitialiser sans prévenir.
     */
    fun loadAll(): LoadResult {
        return try {
            val buttons = readSavedList(KEY_BUTTONS)
            val core = readSavedList(KEY_CORE)
            val quick = readSavedList(KEY_QUICK)
            if (buttons == null && core == null && quick == null) LoadResult(starterLayout(), null, false)
            else {
                val starter by lazy { starterLayout() }
                LoadResult(NinaBackup(buttons ?: starter.buttons, core ?: starter.coreWords.orEmpty(), quick ?: starter.quickFires.orEmpty()), null, false)
            }
        } catch (e: Exception) {
            Log.e("Nina-Debug", "Grille enregistrée illisible", e)
            val latest = AutoBackup.latest(context)
            if (latest != null) LoadResult(latest.second, latest.first, false)
            else LoadResult(starterLayout(), null, true)
        }
    }

    /** Sauvegarde automatique du jour, à partir de ce qui est enregistré (seulement si c'est lisible). */
    fun autoBackupNow() {
        val buttons = runCatching { readSavedList(KEY_BUTTONS) }.getOrNull() ?: return
        val core = runCatching { readSavedList(KEY_CORE) }.getOrNull()
        val quick = runCatching { readSavedList(KEY_QUICK) }.getOrNull()
        runCatching { AutoBackup.save(context.applicationContext, NinaBackup(buttons, core, quick)) }
            .onFailure { Log.e("Nina-Debug", "Sauvegarde automatique impossible", it) }
    }

    /** Copie des données illisibles (pour pouvoir les examiner), avant qu'elles soient remplacées. */
    fun keepUnreadableData() {
        val raw = listOf(KEY_BUTTONS, KEY_CORE, KEY_QUICK).joinToString("\n\n") { "$it = ${prefs.getString(it, null)}" }
        runCatching { File(File(context.filesDir, "sauvegardes").apply { mkdirs() }, "illisible_${System.currentTimeMillis()}.txt").writeText(raw) }
    }

    /** Envoie le fichier de sauvegarde (mail, Drive…), photos comprises. */
    fun shareBackupFile(backup: NinaBackup) {
        try {
            val file = File(context.cacheDir, "sauvegarde_nina.json")
            // Les photos prises avec la tablette partent avec le fichier (sinon elles seraient perdues sur une autre tablette)
            file.writeText(GsonInstance.gson.toJson(backup.withEmbeddedPhotos(context)))
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Sauvegarder la grille de Nina via :"))
        } catch (e: Exception) { e.printStackTrace() }
    }

    companion object {
        const val PREFS = "nina_prefs"
        private const val KEY_BUTTONS = "saved_buttons"
        private const val KEY_CORE = "saved_core_words"
        private const val KEY_QUICK = "saved_quick_fires"

        /** Grille de départ (voir VocabularyPacks pour les grilles prêtes). */
        fun starterLayout(): NinaBackup = VocabularyPacks.default.build()
    }
}
