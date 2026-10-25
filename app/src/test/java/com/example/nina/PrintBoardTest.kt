package com.example.nina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintBoardTest {

    @Test
    fun boardPages_keepsTheScreenOrder_andSplitsIntoPages() {
        // 30 pictos, 3 par rangée, zone de 500 × 300 points : même règle que l'écran (hauteur ≥ 65 % de la largeur)
        val pages = boardPages(30, 3, left = 100f, top = 50f, width = 500f, height = 300f, gap = 8f)
        val perPage = computeGridLayout(30, 3, 500f, 300f, 8f, 0f).perPage
        assertEquals((30 + perPage - 1) / perPage, pages.size)
        assertEquals((0 until 30).toList(), pages.flatten().map { it.index })
        // Rangée par rangée, de gauche à droite, comme à l'écran
        val first = pages[0]
        assertEquals(100f, first[0].left, 0.01f)
        assertTrue(first[1].left > first[0].left)
        assertEquals(first[0].top, first[2].top, 0.01f)
        assertTrue(first[3].top > first[0].top)
        // Tout reste dans la zone
        pages.flatten().forEach { s ->
            assertTrue(s.left >= 100f - 0.01f && s.left + s.width <= 600f + 0.01f)
            assertTrue(s.top >= 50f - 0.01f && s.top + s.height <= 350f + 0.01f)
        }
        assertTrue(boardPages(0, 3, 0f, 0f, 100f, 100f, 4f).isEmpty())
    }

    @Test
    fun photoSampleSize_reducesBigPhotosWithoutGoingBelowTheTarget() {
        assertEquals(1, photoSampleSize(800, 600, 1024))
        assertEquals(2, photoSampleSize(2048, 1536, 1024))
        assertEquals(2, photoSampleSize(4000, 3000, 1024)) // 2000 × 1500 décodé, puis réduit à 1024
        assertEquals(4, photoSampleSize(8000, 6000, 1024))
    }
}
