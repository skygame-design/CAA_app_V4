package com.example.nina

import android.content.Context

/*
 * Opérations sur les listes de pictos (grille, colonne de gauche, phrases rapides) :
 * images à préparer pour le hors réseau, remplacement et déplacement d'un picto retrouvé par son id.
 */

/**
 * Prépare les images pour un usage hors réseau :
 * 1. les pictos marqués "FETCH" reçoivent la première image ARASAAC trouvée pour leur libellé ;
 * 2. toute image encore sur le web est téléchargée dans la tablette (fichier nommé d'après l'id du picto).
 * En cas d'échec (pas de réseau), l'adresse web est gardée et on réessaiera au prochain démarrage.
 */
internal suspend fun updateCellsWithImages(context: Context, vararg lists: MutableList<AACCell>) {
    lists.forEach { list ->
        list.forEachIndexed { i, original ->
            var cell = original
            // Fichier local disparu (sauvegarde restaurée sur une autre tablette) : on repart de l'adresse d'origine
            val local = cell.imageUrl
            if (local != null && local.startsWith("/") && !java.io.File(local).exists() && cell.sourceUrl != null) {
                cell = cell.copy(imageUrl = cell.sourceUrl)
            }
            if (isRemoteImage(cell.imageUrl)) {
                val remote = cell.imageUrl!!
                saveImageLocally(context, remote, "picto_${cell.id}")?.let { path -> cell = cell.copy(imageUrl = path, sourceUrl = remote) }
            }
            // Pas encore d'image, ou image ARASAAC introuvable (certains pictos de la recherche n'ont pas de fichier) :
            // on essaie les résultats ARASAAC suivants pour ce mot, jusqu'à en trouver un qui se télécharge
            val stillRemote = cell.imageUrl
            if (stillRemote == "FETCH" || (stillRemote != null && stillRemote.startsWith("https://static.arasaac.org/"))) {
                try {
                    val candidates = RetrofitInstance.arasaacApi.searchPictograms(cell.label.lowercase())
                        .map { PictoSearch.arasaacUrl(it._id) }
                        .filter { it != stillRemote }
                        .take(5)
                    for (url in candidates) {
                        val path = saveImageLocally(context, url, "picto_${cell.id}")
                        if (path != null) { cell = cell.copy(imageUrl = path, sourceUrl = url); break }
                    }
                } catch (_: Exception) {} // Pas de réseau : on réessaiera au prochain démarrage
            }
            if (cell !== original && i < list.size && list[i].id == original.id) list[i] = cell
            if (cell.isFolder) updateCellsWithImages(context, cell.content)
        }
    }
}

/** Image actuelle du picto (avant modification), pour savoir si l'utilisateur l'a changée. */
internal fun targetCellImage(cell: AACCell, vararg lists: List<AACCell>): String? =
    lists.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == cell.id } }?.imageUrl

/** Position d'un picto dans la liste, retrouvé par son id (même s'il a été modifié entre-temps). */
internal fun List<AACCell>.indexOfCell(cell: AACCell): Int = indexOfFirst { it.id == cell.id }

/**
 * Remplace le picto de même id par sa version modifiée, dans la liste où il se trouve.
 * Renvoie la version modifiée, ou null si le picto n'a pas été trouvé.
 */
internal fun replaceCellInLists(edited: AACCell, vararg lists: MutableList<AACCell>): AACCell? {
    var found = false
    lists.forEach { list ->
        val idx = list.indexOfCell(edited)
        if (idx != -1) {
            list[idx] = edited
            found = true
        }
    }
    return if (found) edited else null
}

/**
 * Retire la cellule de la liste où elle se trouve (grille, Core Words ou Quick Fires) et l'ajoute à la destination.
 */
internal fun moveCell(cell: AACCell, destination: MutableList<AACCell>, vararg sources: MutableList<AACCell>) {
    val source = sources.firstOrNull { it.indexOfCell(cell) != -1 } ?: return
    if (source === destination) return
    source.removeAt(source.indexOfCell(cell))
    destination.add(cell)
}
