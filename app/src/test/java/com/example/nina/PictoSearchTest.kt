package com.example.nina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictoSearchTest {

    @Test
    fun globalSymbols_namesSetsAndSkipsArasaac() {
        val labels = listOf(
            GlobalSymbolsLabel("chat", GlobalSymbolsPicto(13, "https://gs/mulberry.svg")),
            GlobalSymbolsLabel("chat", GlobalSymbolsPicto(17, "https://gs/arasaac.png")), // déjà fourni par ARASAAC
            GlobalSymbolsLabel("chat", GlobalSymbolsPicto(81, "https://gs/sclera.png")),
            GlobalSymbolsLabel("chat", GlobalSymbolsPicto(999, "https://gs/autre.png")),
            GlobalSymbolsLabel("chat", null)
        )
        val results = PictoSearch.fromGlobalSymbols(labels)
        assertEquals(listOf("Mulberry", "Sclera", "Global Symbols"), results.map { it.source })
        assertEquals("CC BY-SA 4.0", results[0].licence)
    }

    @Test
    fun openSymbols_skipsBanksCoveredElsewhere() {
        val results = PictoSearch.fromOpenSymbols(listOf(
            OpenSymbolsResult("chat", "https://os/a.png", "arasaac", "CC BY-NC-SA"),
            OpenSymbolsResult("chat", "https://os/m.svg", "mulberry", "CC BY-SA"),
            OpenSymbolsResult("chat", "https://os/t.png", "tawasol", "CC BY-SA"),
            OpenSymbolsResult("chat", "https://os/e.svg", "twemoji", "CC BY")
        ))
        assertEquals(listOf("Tawasol", "Twemoji"), results.map { it.source })
    }

    @Test
    fun merge_removesDuplicatesAndPutsArasaacFirst() {
        val merged = PictoSearch.merge(
            listOf(PictoResult("u1", "Tawasol", "")),
            listOf(PictoResult("u2", "ARASAAC", ""), PictoResult("u1", "Tawasol", "")),
            listOf(PictoResult("u3", "Mulberry", ""))
        )
        assertEquals(listOf("ARASAAC", "Mulberry", "Tawasol"), merged.map { it.source })
    }

    @Test
    fun imageExtension_keepsSvgAndJpg() {
        assertEquals("svg", imageExtension("https://x/a.svg", "image/svg+xml", ByteArray(0)))
        assertEquals("svg", imageExtension("https://x/a", null, "<?xml version=\"1.0\"?><svg xmlns=".toByteArray()))
        assertEquals("jpg", imageExtension("https://x/photo.JPG?v=2", null, byteArrayOf(1, 2)))
        assertEquals("png", imageExtension("https://x/a.png", "image/png", byteArrayOf(1, 2)))
    }

    @Test
    fun remoteImages() {
        assertTrue(isRemoteImage("https://static.arasaac.org/x.png"))
        assertFalse(isRemoteImage("/data/user/0/com.example.nina/files/picto_1.png"))
        assertFalse(isRemoteImage("FETCH"))
        assertFalse(isRemoteImage(null))
    }
}
