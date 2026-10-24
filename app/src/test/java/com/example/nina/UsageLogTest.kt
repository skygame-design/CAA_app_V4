package com.example.nina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class UsageLogTest {

    private val today = LocalDate.of(2026, 10, 24)
    private fun at(daysAgo: Long, hour: Int = 10) = today.minusDays(daysAgo).atTime(hour, 0)

    private val events = listOf(
        UsageEvent(at(0), UsageType.WORD, "manger"),
        UsageEvent(at(0, 11), UsageType.WORD, "manger"),
        UsageEvent(at(0, 12), UsageType.WORD, "boire"),
        UsageEvent(at(0, 12), UsageType.SENTENCE, "Je veux manger"),
        UsageEvent(at(3), UsageType.WORD, "dormir"),
        UsageEvent(at(20), UsageType.WORD, "manger"),
    )

    @Test
    fun lineRoundTrip_withQuotesAndSemicolons() {
        val event = UsageEvent(LocalDateTime.of(2026, 10, 24, 14, 3, 12), UsageType.SENTENCE, "Il a dit \"oui\" ; puis non")
        val line = UsageLog.formatLine(event)
        assertEquals("2026-10-24T14:03:12;phrase;\"Il a dit \"\"oui\"\" ; puis non\"", line)
        assertEquals(event, UsageLog.parseLine(line))
        assertNull(UsageLog.parseLine("ligne abîmée"))
    }

    @Test
    fun countsByPeriod() {
        val week = UsageLog.since(events, today.minusDays(6))
        assertEquals(listOf("manger" to 2, "boire" to 1, "dormir" to 1), UsageLog.wordCounts(week))
        assertEquals(listOf("manger" to 3, "boire" to 1, "dormir" to 1), UsageLog.wordCounts(events))
    }

    @Test
    fun dailyCounts_includeEmptyDays() {
        val daily = UsageLog.dailyWordCounts(events, 4, today)
        assertEquals(listOf(today.minusDays(3) to 1, today.minusDays(2) to 0, today.minusDays(1) to 0, today to 3), daily)
    }

    @Test
    fun csvExport() {
        val csv = UsageLog.toCsv(events.take(1) + events[3])
        assertEquals(
            "Date;Heure;Type;Texte\n24/10/2026;10:00:00;Mot;\"manger\"\n24/10/2026;12:00:00;Phrase;\"Je veux manger\"\n",
            csv
        )
    }
}
