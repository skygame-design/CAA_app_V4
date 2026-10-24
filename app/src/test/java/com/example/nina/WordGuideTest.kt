package com.example.nina

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordGuideTest {

    private val eau = AACCell("Eau", 0L)
    private val boissons = AACCell("Boissons", 0L, isFolder = true, content = mutableStateListOf(eau))
    private val nourriture = AACCell("Nourriture", 0L, isFolder = true, content = mutableStateListOf(boissons))
    private val secret = AACCell("Eau secrète", 0L, isHidden = true)
    private val buttons = listOf(AACCell("Moi", 0L), nourriture, secret)

    @Test
    fun pathGoesThroughEachFolder() {
        val guide = findWordGuides("eau", buttons, emptyList(), emptyList()).single()
        assertEquals(listOf("Nourriture", "Boissons", "Eau"), guide.steps.map { it.label })
        assertEquals("Accueil > Nourriture > Boissons", guide.place)
    }

    @Test
    fun foldersInLeftColumn_giveFolderThenWord() {
        val guide = findWordGuides("eau", emptyList(), listOf(nourriture), emptyList()).single()
        assertEquals(listOf("Nourriture", "Boissons", "Eau"), guide.steps.map { it.label })
        assertEquals("Colonne de gauche > Nourriture > Boissons", guide.place)
    }

    @Test
    fun allFolders_includesSubFolders() {
        assertEquals(listOf("Nourriture", "Boissons"), allFolders(buttons).map { it.label })
    }

    @Test
    fun searchesSidebars_andSkipsHidden() {
        val core = listOf(AACCell("Vouloir", 0L))
        val guides = findWordGuides("vou", buttons, core, emptyList())
        assertEquals(listOf("Vouloir"), guides.map { it.cell.label })
        assertEquals("Colonne de gauche", guides.single().place)
        assertTrue(findWordGuides("secrète", buttons, emptyList(), emptyList()).isEmpty())
    }
}
