package com.example.nina

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Type d'événement : un picto touché, ou une phrase prononcée avec le bouton Play. */
enum class UsageType(val code: String, val label: String) {
    WORD("mot", "Mot"), SENTENCE("phrase", "Phrase");

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code }
    }
}

data class UsageEvent(val time: LocalDateTime, val type: UsageType, val text: String)

/**
 * Journal d'utilisation daté, stocké localement (fichier usage_log.csv dans le stockage interne).
 * Une ligne par événement : 2026-10-24T14:03:12;mot;"manger"
 * Sert aux statistiques par période, au graphique par jour et à l'export pour l'orthophoniste.
 */
object UsageLog {
    private const val FILE_NAME = "usage_log.csv"
    private val lock = Any()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun append(context: Context, type: UsageType, text: String, time: LocalDateTime = LocalDateTime.now()) {
        if (text.isBlank()) return
        synchronized(lock) {
            file(context).appendText(formatLine(UsageEvent(time, type, text)) + "\n")
        }
    }

    fun read(context: Context): List<UsageEvent> = synchronized(lock) {
        val f = file(context)
        if (!f.exists()) emptyList() else f.readLines().mapNotNull { parseLine(it) }
    }

    fun clear(context: Context) = synchronized(lock) { file(context).delete(); Unit }

    /**
     * Fichier CSV lisible par Excel / LibreOffice (séparateur ";", UTF-8 avec BOM),
     * écrit dans le cache pour être partagé.
     */
    fun exportCsv(context: Context, events: List<UsageEvent>): File {
        val out = File(context.cacheDir, "statistiques_nina_${LocalDate.now()}.csv")
        out.writeText("﻿" + toCsv(events), Charsets.UTF_8)
        return out
    }

    // --- Fonctions pures (testées sans Android) ---

    fun formatLine(event: UsageEvent): String {
        val text = event.text.replace('\n', ' ').replace('\r', ' ').replace("\"", "\"\"")
        return "${event.time.truncatedTo(ChronoUnit.SECONDS)};${event.type.code};\"$text\""
    }

    fun parseLine(line: String): UsageEvent? {
        val parts = line.split(';', limit = 3)
        if (parts.size < 3) return null
        val time = runCatching { LocalDateTime.parse(parts[0]) }.getOrNull() ?: return null
        val type = UsageType.fromCode(parts[1]) ?: return null
        val text = parts[2].removeSurrounding("\"").replace("\"\"", "\"")
        return UsageEvent(time, type, text)
    }

    fun toCsv(events: List<UsageEvent>): String {
        val date = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val hour = DateTimeFormatter.ofPattern("HH:mm:ss")
        return buildString {
            append("Date;Heure;Type;Texte\n")
            events.forEach { e ->
                val text = e.text.replace("\"", "\"\"")
                append("${e.time.format(date)};${e.time.format(hour)};${e.type.label};\"$text\"\n")
            }
        }
    }

    /** Événements depuis cette date (incluse), ou tous si since est null. */
    fun since(events: List<UsageEvent>, since: LocalDate?): List<UsageEvent> =
        if (since == null) events else events.filter { !it.time.toLocalDate().isBefore(since) }

    /** Mots les plus utilisés, du plus fréquent au moins fréquent. */
    fun wordCounts(events: List<UsageEvent>): List<Pair<String, Int>> =
        events.filter { it.type == UsageType.WORD }
            .groupingBy { it.text }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

    /** Nombre de mots par jour sur les `days` derniers jours (jours sans activité inclus, à 0). */
    fun dailyWordCounts(events: List<UsageEvent>, days: Int, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Int>> {
        val counts = events.filter { it.type == UsageType.WORD }.groupingBy { it.time.toLocalDate() }.eachCount()
        return (days - 1 downTo 0).map { offset -> today.minusDays(offset.toLong()).let { it to (counts[it] ?: 0) } }
    }
}
