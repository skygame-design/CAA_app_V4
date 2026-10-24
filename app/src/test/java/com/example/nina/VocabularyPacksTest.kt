package com.example.nina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyPacksTest {

    @Test
    fun eachWordHasASinglePlace() {
        VocabularyPacks.all.forEach { pack ->
            val labels = VocabularyPacks.allLabels(pack.build()).map { it.lowercase() }
            val duplicates = labels.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertTrue("${pack.name} : mots en double $duplicates", duplicates.isEmpty())
        }
    }

    @Test
    fun homeWordsKeepTheirPositionFromOneLevelToTheNext() {
        // Position (rangée, colonne) de chaque mot de l'accueil
        fun positions(pack: VocabularyPack) = pack.build().buttons
            .mapIndexed { i, cell -> cell.label to (i / pack.columns to i % pack.columns) }.toMap()

        VocabularyPacks.all.zipWithNext().forEach { (lower, higher) ->
            val before = positions(lower)
            val after = positions(higher)
            before.forEach { (label, place) ->
                assertEquals("« $label » doit garder sa place de ${lower.name} à ${higher.name}", place, after[label])
            }
        }
    }

    @Test
    fun packsFitTheScreenWithoutPages() {
        VocabularyPacks.all.forEach { pack ->
            val layout = pack.build()
            // Accueil : tient sur une page avec le nombre de pictos par rangée conseillé (zone de grille de la tablette)
            val grid = computeGridLayout(layout.buttons.size, pack.columns, width = 900f, height = 650f, gap = 16f, pagerHeight = 72f)
            assertEquals("${pack.name} : accueil sur une page", 1, grid.pageCount)
            // Colonnes : au plus 6 dossiers et 7 phrases rapides (pas de pages)
            assertTrue("${pack.name} : trop de dossiers", layout.coreWords.orEmpty().size <= 6)
            assertTrue("${pack.name} : trop de phrases rapides", layout.quickFires.orEmpty().size <= 7)
            assertTrue("${pack.name} : dossiers uniquement à gauche", layout.coreWords.orEmpty().all { it.isFolder })
        }
    }

    @Test
    fun ambiguousWordsUseHandPickedPictos() {
        val labels = VocabularyPacks.all.flatMap { pack ->
            fun collect(cells: List<AACCell>): List<AACCell> = cells.flatMap { listOf(it) + collect(it.content) }
            val l = pack.build(); collect(l.buttons) + collect(l.coreWords.orEmpty()) + collect(l.quickFires.orEmpty())
        }
        labels.filter { it.label.lowercase() in VocabularyPacks.PINNED_ARASAAC }.forEach {
            assertEquals(PictoSearch.arasaacUrl(VocabularyPacks.PINNED_ARASAAC.getValue(it.label.lowercase())), it.imageUrl)
        }
    }
}
