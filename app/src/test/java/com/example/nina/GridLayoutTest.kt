package com.example.nina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridLayoutTest {

    // Zone de grille de la tablette Medium_Tablet en paysage, en dp
    private fun layout(items: Int, columns: Int) = computeGridLayout(items, columns, width = 1100f, height = 650f, gap = 16f, pagerHeight = 72f)

    @Test
    fun everyColumnCountFitsWithoutScrolling() {
        (1..8).forEach { columns ->
            val l = layout(16, columns)
            assertTrue("au moins une rangée en $columns colonnes", l.rows >= 1)
            assertEquals(columns * l.rows, l.perPage)
            assertEquals("toutes les pages couvrent les 16 pictos", (16 + l.perPage - 1) / l.perPage, l.pageCount)
        }
    }

    @Test
    fun fourColumns_threeRowsAndTwoPages() {
        assertEquals(GridLayout(rows = 3, perPage = 12, pageCount = 2), layout(16, 4))
        assertEquals(GridLayout(rows = 3, perPage = 12, pageCount = 1), layout(12, 4))
    }

    @Test
    fun extremes() {
        // 1 picto par rangée : un seul grand picto par page
        assertEquals(GridLayout(rows = 1, perPage = 1, pageCount = 16), layout(16, 1))
        // 8 pictos par rangée : tout tient sur une page
        assertEquals(1, layout(16, 8).pageCount)
        // Dossier presque vide : une seule page, même taille de case qu'à l'accueil
        assertEquals(layout(12, 4).rows, layout(2, 4).rows)
    }
}
