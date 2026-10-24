package com.example.nina

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.util.UUID

/**
 * Modèle de données pour un bouton AAC.
 * Utilise SnapshotStateList pour que Compose détecte les ajouts à l'intérieur des dossiers.
 *
 * - id : identifiant unique, pour distinguer deux pictos identiques et retrouver un picto même après modification.
 * - spokenText : texte prononcé quand on touche le picto (ex. "Toilettes" -> "J'ai besoin d'aller aux toilettes").
 *   Le libellé (label) reste ce qui est ajouté à la phrase.
 */
data class AACCell(
    val label: String,
    val colorLong: Long,
    val imageUrl: String? = null,
    val audioPath: String? = null,
    val isFolder: Boolean = false,
    val isQuickPhrase: Boolean = false,
    val isHidden: Boolean = false,
    val imageRes: Int? = null,
    val content: SnapshotStateList<AACCell> = mutableStateListOf(),
    val id: String = newCellId(),
    val spokenText: String? = null
) {
    /** Ce que la synthèse vocale dit quand on touche le picto. */
    val textToSpeak: String
        get() = spokenText?.takeIf { it.isNotBlank() } ?: label
}

fun newCellId(): String = UUID.randomUUID().toString()

/**
 * Gson ignore les valeurs par défaut Kotlin : les pictos enregistrés avant l'ajout de l'id
 * reviennent avec id = null (et parfois content = null). On les complète ici, récursivement.
 */
@Suppress("USELESS_CAST")
fun normalizeCells(cells: List<AACCell>?): List<AACCell> = (cells ?: emptyList()).map { cell ->
    val children = normalizeCells(cell.content as List<AACCell>?)
    cell.copy(
        id = (cell.id as String?) ?: newCellId(),
        content = mutableStateListOf<AACCell>().apply { addAll(children) }
    )
}
