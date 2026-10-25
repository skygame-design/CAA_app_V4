package com.example.nina

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DragDropLogicTest {
    private fun cell(label: String) = AACCell(label, 0xFFFFFFFFL)
    private fun list(vararg labels: String) = labels.map { cell(it) }.toMutableList()
    private fun List<AACCell>.labels() = joinToString("") { it.label }

    /** Dépôt d'un picto de la zone `from` sur la case `target` de la zone `to`. */
    private fun drop(
        dropped: AACCell, from: DropZone, target: AACCell?, to: DropZone?, mode: DropTargetMode,
        core: MutableList<AACCell>, quick: MutableList<AACCell>, grid: MutableList<AACCell>, over: DropZone? = to
    ) = applyDrop(dropped, from, target, to, mode, over, core, quick, grid)

    @Test
    fun grid_swap_insertBefore_insertAfter() {
        val g1 = list("a", "b", "c", "d")
        assertEquals(DropOutcome.GRID, drop(g1[0], DropZone.GRID, g1[2], DropZone.GRID, DropTargetMode.SWAP, mutableListOf(), mutableListOf(), g1))
        assertEquals("cbad", g1.labels())
        val g2 = list("a", "b", "c", "d")
        drop(g2[3], DropZone.GRID, g2[1], DropZone.GRID, DropTargetMode.INSERT_BEFORE, mutableListOf(), mutableListOf(), g2)
        assertEquals("adbc", g2.labels())
        val g3 = list("a", "b", "c", "d")
        drop(g3[0], DropZone.GRID, g3[2], DropZone.GRID, DropTargetMode.INSERT_AFTER, mutableListOf(), mutableListOf(), g3)
        assertEquals("bcad", g3.labels())
    }

    @Test
    fun columns_canBeReorganised() {
        // Colonne de gauche : déplacer le 3e dossier tout en haut
        val core = list("x", "y", "z")
        assertEquals(DropOutcome.CORE, drop(core[2], DropZone.CORE, core[0], DropZone.CORE, DropTargetMode.INSERT_BEFORE, core, mutableListOf(), mutableListOf()))
        assertEquals("zxy", core.labels())
        // Phrases rapides : échange
        val quick = list("p", "q", "r")
        assertEquals(DropOutcome.QUICK, drop(quick[0], DropZone.QUICK, quick[2], DropZone.QUICK, DropTargetMode.SWAP, mutableListOf(), quick, mutableListOf()))
        assertEquals("rqp", quick.labels())
    }

    @Test
    fun columnToGrid_addsACopyAtTheTargetedPlace() {
        val core = list("x", "y"); val grid = list("a", "b", "c")
        assertEquals(DropOutcome.GRID, drop(core[1], DropZone.CORE, grid[1], DropZone.GRID, DropTargetMode.INSERT_AFTER, core, mutableListOf(), grid))
        assertEquals("abyc", grid.labels())
        assertEquals("xy", core.labels()) // L'original reste dans la colonne
        assertNotEquals(core[1].id, grid[2].id) // Copie avec un nouvel id
        // Phrase rapide lâchée dans la grille sans viser de case : à la fin, ce n'est plus une phrase rapide
        val quick = mutableListOf(cell("oui").copy(isQuickPhrase = true))
        drop(quick[0], DropZone.QUICK, null, null, DropTargetMode.NONE, core, quick, grid, over = DropZone.GRID)
        assertEquals("abycoui", grid.labels())
        assertEquals(false, grid.last().isQuickPhrase)
    }

    @Test
    fun gridToColumn_addsACopyOnce() {
        val grid = list("a", "b"); val core = mutableListOf<AACCell>(); val quick = list("q")
        assertEquals(DropOutcome.CORE, drop(grid[1], DropZone.GRID, null, null, DropTargetMode.NONE, core, quick, grid, over = DropZone.CORE))
        assertEquals(DropOutcome.NONE, drop(grid[1], DropZone.GRID, null, null, DropTargetMode.NONE, core, quick, grid, over = DropZone.CORE)) // Déjà là
        assertEquals(1, core.size)
        assertEquals(DropOutcome.QUICK, drop(grid[0], DropZone.GRID, quick[0], DropZone.QUICK, DropTargetMode.INSERT_BEFORE, core, quick, grid))
        assertEquals("aq", quick.labels())
        assertTrue(quick[0].isQuickPhrase)
        assertEquals("ab", grid.labels()) // La grille ne bouge pas
    }

    @Test
    fun copiedFolder_hasItsOwnContent() {
        val folder = cell("dossier").copy(isFolder = true, content = mutableStateListOf(cell("m"), cell("n")))
        val core = mutableListOf(folder); val grid = list("a")
        drop(folder, DropZone.CORE, null, null, DropTargetMode.NONE, core, mutableListOf(), grid, over = DropZone.GRID)
        val copy = grid.last()
        copy.content.removeAt(0)
        assertEquals("mn", folder.content.labels()) // Modifier la copie ne touche pas l'original
        assertNotEquals(folder.content[1].id, copy.content[0].id)
    }

    @Test
    fun dropOutsideTheDisplayedList_changesNothing() {
        val g = list("a", "b", "c", "d"); val other = cell("x")
        assertEquals(DropOutcome.NONE, drop(other, DropZone.GRID, g[0], DropZone.GRID, DropTargetMode.SWAP, mutableListOf(), mutableListOf(), g))
        assertEquals(DropOutcome.NONE, drop(g[0], DropZone.GRID, null, null, DropTargetMode.SWAP, mutableListOf(), mutableListOf(), g, over = null))
        assertEquals("abcd", g.labels())
    }

    @Test
    fun dropMode_dependsOnWhereTheFingerIsInTheCell() {
        assertEquals(DropTargetMode.INSERT_BEFORE, dropModeAt(10f, 100f))
        assertEquals(DropTargetMode.SWAP, dropModeAt(50f, 100f))
        assertEquals(DropTargetMode.INSERT_AFTER, dropModeAt(90f, 100f))
    }
}
