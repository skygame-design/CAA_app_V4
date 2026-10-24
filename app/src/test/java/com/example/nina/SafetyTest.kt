package com.example.nina

import android.speech.tts.TextToSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AutoBackupTest {

    @Test
    fun fileNamesAndDates() {
        val day = LocalDate.of(2026, 10, 24)
        assertEquals("nina_2026-10-24.json", AutoBackup.fileName(day))
        assertEquals(day, AutoBackup.dateOf("nina_2026-10-24.json"))
        assertNull(AutoBackup.dateOf("nina_2026-10-24.json.tmp"))
        assertNull(AutoBackup.dateOf("illisible_1792856986498.txt"))
    }

    @Test
    fun keepsOnlyTheSevenMostRecentDays() {
        val names = (1..10).map { AutoBackup.fileName(LocalDate.of(2026, 10, it)) } + "illisible_1.txt"
        assertEquals(
            listOf("nina_2026-10-03.json", "nina_2026-10-02.json", "nina_2026-10-01.json"),
            AutoBackup.namesToDelete(names.shuffled())
        )
    }
}

class VoiceCheckTest {

    private fun fr(network: Boolean, notInstalled: Boolean = false) = VoiceInfo("fr", network, notInstalled)

    @Test
    fun offlineFrenchVoice_isOk() {
        assertEquals(VoiceStatus.OK, VoiceCheck.evaluate(TextToSpeech.LANG_COUNTRY_AVAILABLE, listOf(fr(true), fr(false), VoiceInfo("en", false, false))))
    }

    @Test
    fun onlyNetworkFrenchVoice_warns() {
        assertEquals(VoiceStatus.NETWORK_ONLY, VoiceCheck.evaluate(TextToSpeech.LANG_COUNTRY_AVAILABLE, listOf(fr(true), fr(false, notInstalled = true))))
    }

    @Test
    fun missingFrenchVoice() {
        assertEquals(VoiceStatus.MISSING, VoiceCheck.evaluate(TextToSpeech.LANG_MISSING_DATA, emptyList()))
        assertEquals(VoiceStatus.MISSING, VoiceCheck.evaluate(TextToSpeech.LANG_AVAILABLE, listOf(VoiceInfo("en", false, false))))
        assertEquals(VoiceStatus.MISSING, VoiceCheck.evaluate(TextToSpeech.LANG_AVAILABLE, listOf(fr(false, notInstalled = true))))
    }

    @Test
    fun engineWithoutVoiceList_trustsSetLanguage() {
        assertEquals(VoiceStatus.OK, VoiceCheck.evaluate(TextToSpeech.LANG_AVAILABLE, emptyList()))
    }
}
