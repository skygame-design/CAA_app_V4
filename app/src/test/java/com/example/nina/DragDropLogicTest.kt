package com.example.nina

import org.junit.Assert.assertEquals
import org.junit.Test

class DragDropLogicTest {
    private fun cell(label: String) = AACCell(label, 0xFFFFFFFFL)
    private fun grid() = mutableListOf(cell("a"), cell("b"), cell("c"), cell("d"))
    private fun List<AACCell>.labels() = joinToString("") { it.label }

    @Test
    fun swap_insertBefore_insertAfter_inTheDisplayedGrid() {
        val g1 = grid()
        assertEquals(DropOutcome.GRID, applyDrop(g1[0], g1[2], DropTargetMode.SWAP, false, false, mutableListOf(), mutableListOf(), g1))
        assertEquals("cbad", g1.labels())

        val g2 = grid()
        applyDrop(g2[3], g2[1], DropTargetMode.INSERT_BEFORE, false, false, mutableListOf(), mutableListOf(), g2)
        assertEquals("adbc", g2.labels())

        val g3 = grid()
        applyDrop(g3[0], g3[2], DropTargetMode.INSERT_AFTER, false, false, mutableListOf(), mutableListOf(), g3)
        assertEquals("bcad", g3.labels())
    }

    @Test
    fun dropOnAColumn_addsACopyOnce() {
        val g = grid(); val core = mutableListOf<AACCell>(); val quick = mutableListOf<AACCell>()
        assertEquals(DropOutcome.CORE, applyDrop(g[1], null, DropTargetMode.NONE, true, false, core, quick, g))
        assertEquals(DropOutcome.NONE, applyDrop(g[1], null, DropTargetMode.NONE, true, false, core, quick, g)) // Déjà là
        assertEquals(1, core.size)
        assertEquals(true, core[0].id != g[1].id) // Copie avec un nouvel id
        assertEquals(DropOutcome.QUICK, applyDrop(g[2], null, DropTargetMode.NONE, false, true, core, quick, g))
        assertEquals(true, quick[0].isQuickPhrase)
        assertEquals("abcd", g.labels()) // La grille ne bouge pas
    }

    @Test
    fun dropOutsideTheDisplayedList_changesNothing() {
        // Cas du bug corrigé : la case visée n'est pas dans la liste affichée (dossier ouvert) → rien ne bouge
        val g = grid(); val other = cell("x")
        assertEquals(DropOutcome.NONE, applyDrop(other, g[0], DropTargetMode.SWAP, false, false, mutableListOf(), mutableListOf(), g))
        assertEquals(DropOutcome.NONE, applyDrop(g[0], null, DropTargetMode.SWAP, false, false, mutableListOf(), mutableListOf(), g))
        assertEquals("abcd", g.labels())
    }

    @Test
    fun dropMode_dependsOnWhereTheFingerIsInTheCell() {
        assertEquals(DropTargetMode.INSERT_BEFORE, dropModeAt(10f, 100f))
        assertEquals(DropTargetMode.SWAP, dropModeAt(50f, 100f))
        assertEquals(DropTargetMode.INSERT_AFTER, dropModeAt(90f, 100f))
    }
}
