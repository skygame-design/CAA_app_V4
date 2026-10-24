package com.example.nina

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BackupMigrationTest {

    // Ancien format : liste de boutons, sans id ni spokenText
    private val oldJson = """
        [
          {"label":"Moi","colorLong":4294964676,"imageUrl":"FETCH","isFolder":false,"content":[]},
          {"label":"Moi","colorLong":4294964676,"imageUrl":"FETCH","isFolder":false,"content":[]},
          {"label":"Nourriture","colorLong":4294954112,"isFolder":true,
           "content":[{"label":"Eau","colorLong":4294954112,"content":[]}]}
        ]
    """.trimIndent()

    @Test
    fun oldBackup_getsUniqueIds_includingInsideFolders() {
        val backup = parseBackup(oldJson)
        val ids = backup.buttons.map { it.id } + backup.buttons[2].content.map { it.id }

        ids.forEach { assertNotNull(it) }
        assertEquals("Chaque picto doit avoir un id différent", ids.size, ids.toSet().size)
        assertNull(backup.coreWords)
        assertEquals("Moi", backup.buttons[0].textToSpeak)
    }

    @Test
    fun newBackup_keepsIdsAndSpokenText() {
        val original = NinaBackup(
            buttons = listOf(AACCell("Toilettes", 0xFFE1BEE7L, spokenText = "J'ai besoin d'aller aux toilettes")),
            coreWords = listOf(AACCell("Vouloir", 0xFFC8E6C9L)),
            quickFires = emptyList()
        )
        val restored = parseBackup(GsonInstance.gson.toJson(original))

        assertEquals(original.buttons[0].id, restored.buttons[0].id)
        assertEquals("J'ai besoin d'aller aux toilettes", restored.buttons[0].textToSpeak)
        assertEquals("Vouloir", restored.coreWords!![0].label)
    }
}
