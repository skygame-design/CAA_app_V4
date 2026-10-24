package com.example.nina

import org.junit.Assert.assertEquals
import org.junit.Test

class ColumnLayoutTest {

    // Colonne de gauche de la tablette : environ 652 dp, moins la case Accueil (80 dp + 12 dp d'espace)
    private val height = 652f - 80f - 12f
    private fun layout(items: Int) = computeColumnLayout(items, height, minItemHeight = 80f, gap = 12f, pagerHeight = 52f)

    @Test
    fun upToSixFolders_fitWithoutPages() {
        assertEquals(GridLayout(rows = 6, perPage = 6, pageCount = 1), layout(5)) // disposition recommandée : 5 dossiers
        assertEquals(1, layout(6).pageCount)
    }

    @Test
    fun moreFolders_usePagesWithStableSlots() {
        // Une place cédée aux boutons ▲ ▼ : 5 dossiers par page
        assertEquals(GridLayout(rows = 5, perPage = 5, pageCount = 2), layout(7))
        assertEquals(GridLayout(rows = 5, perPage = 5, pageCount = 2), layout(10))
    }
}
