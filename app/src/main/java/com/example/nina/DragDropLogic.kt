package com.example.nina

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/*
 * Glisser-déposer des pictos en mode adulte (appui long sur un picto, puis glisser) :
 * suivi du doigt sur tout l'écran, case survolée et mode de dépôt, puis application du dépôt.
 */

/** Zone de l'écran d'où part un picto, ou où il est déposé. */
enum class DropZone { CORE, GRID, QUICK }

/** Liste modifiée par un dépôt (pour savoir quoi enregistrer). */
enum class DropOutcome { CORE, QUICK, GRID, NONE }

/** Copie indépendante d'un picto : nouvel id, et pour un dossier, sa propre copie du contenu (rien n'est partagé). */
fun AACCell.independentCopy(): AACCell =
    copy(id = newCellId(), content = mutableStateListOf<AACCell>().apply { addAll(content.map { it.independentCopy() }) })

/**
 * Applique un dépôt. Fonction pure, testée sans Android.
 * - Dans la même zone (grille, colonne de gauche ou phrases rapides) : échange, ou insertion avant / après la case visée.
 * - Vers une autre zone : une copie y est ajoutée, à côté de la case visée (ou à la fin si on lâche dans la zone
 *   sans viser de case), sauf si le mot y est déjà. L'original reste à sa place.
 * @param targetZone zone de la case visée (null : aucune case visée)
 * @param overZone zone sous le doigt au moment du lâcher, même sans case visée (null : hors des trois zones)
 */
fun applyDrop(
    dropped: AACCell, from: DropZone,
    target: AACCell?, targetZone: DropZone?, mode: DropTargetMode, overZone: DropZone?,
    coreWords: MutableList<AACCell>, quickFires: MutableList<AACCell>, grid: MutableList<AACCell>
): DropOutcome {
    val dest = targetZone ?: overZone ?: return DropOutcome.NONE
    val list = when (dest) { DropZone.CORE -> coreWords; DropZone.QUICK -> quickFires; DropZone.GRID -> grid }
    val outcome = when (dest) { DropZone.CORE -> DropOutcome.CORE; DropZone.QUICK -> DropOutcome.QUICK; DropZone.GRID -> DropOutcome.GRID }
    val targetInList = target?.takeIf { targetZone == dest && list.indexOfCell(it) != -1 }

    if (dest == from) {
        // Réorganisation dans la même zone
        if (targetInList == null || targetInList.id == dropped.id) return DropOutcome.NONE
        val draggedIndex = list.indexOfCell(dropped)
        val targetIndex = list.indexOfCell(targetInList)
        if (draggedIndex == -1) return DropOutcome.NONE
        when (mode) {
            DropTargetMode.SWAP -> {
                val temp = list[draggedIndex]
                list[draggedIndex] = list[targetIndex]
                list[targetIndex] = temp
            }
            DropTargetMode.INSERT_BEFORE, DropTargetMode.INSERT_AFTER -> {
                val moved = list.removeAt(draggedIndex)
                var newIndex = list.indexOfCell(targetInList)
                if (mode == DropTargetMode.INSERT_AFTER) newIndex += 1
                list.add(newIndex, moved)
            }
            else -> return DropOutcome.NONE
        }
        return outcome
    }

    // Vers une autre zone : copie (le mot n'est pas ajouté deux fois dans la même zone)
    if (list.any { it.label == dropped.label }) return DropOutcome.NONE
    val copy = dropped.independentCopy().copy(isQuickPhrase = dest == DropZone.QUICK)
    if (targetInList != null) {
        val index = list.indexOfCell(targetInList) + if (mode == DropTargetMode.INSERT_AFTER) 1 else 0
        list.add(index, copy)
    } else {
        list.add(copy)
    }
    return outcome
}

/** Mode de dépôt selon l'endroit dans la case visée : début = avant, fin = après, milieu = échange (en largeur ou en hauteur). */
fun dropModeAt(relative: Float, size: Float): DropTargetMode = when {
    relative < size * 0.25f -> DropTargetMode.INSERT_BEFORE
    relative > size * 0.75f -> DropTargetMode.INSERT_AFTER
    else -> DropTargetMode.SWAP
}

/**
 * Suivi du doigt sur tout l'écran pendant un glisser-déposer (lancé par un appui long dans une case).
 * Créé une seule fois : les positions des cases et onDrop sont lus à chaque événement, pour toujours avoir la version à jour
 * (sinon le dépôt travaillait sur la liste de l'accueil même dans un dossier ouvert).
 * @param targets positions (dans l'écran) des cases visibles de chaque zone
 */
fun Modifier.dragAndDropTracking(
    dnd: DragAndDropState,
    targets: () -> List<Pair<DropZone, Map<AACCell, Rect>>>,
    onDrop: (Offset, AACCell) -> Unit
): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            // Lecture prioritaire sans bloquer les enfants (sauf si consume() est appelé)
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (!dnd.isDragging) continue
            val change = event.changes.firstOrNull()
            if (change == null || !change.pressed) {
                Log.d("Nina-Debug", "🏠 Root : Relâché détecté, exécution du Drop")
                dnd.draggedCell?.let { onDrop(dnd.dragOffset, it) }
                dnd.isDragging = false
                dnd.draggedCell = null
            } else {
                dnd.dragOffset = change.position
                // Case survolée et mode de dépôt, en temps réel (pour le retour visuel) ;
                // dans les colonnes, avant / après se lit en hauteur
                val position = dnd.dragOffset
                val hovered = targets().firstNotNullOfOrNull { (zone, bounds) ->
                    bounds.entries.firstOrNull { (_, rect) -> rect.contains(position) }?.let { Triple(zone, it.key, it.value) }
                }
                if (hovered != null && hovered.second.id != dnd.draggedCell?.id) {
                    val (zone, cell, rect) = hovered
                    val vertical = zone != DropZone.GRID
                    dnd.currentTargetCell = cell
                    dnd.currentTargetZone = zone
                    dnd.targetVertical = vertical
                    dnd.currentDropMode = if (vertical) dropModeAt(position.y - rect.top, rect.height) else dropModeAt(position.x - rect.left, rect.width)
                } else {
                    dnd.currentTargetCell = null
                    dnd.currentTargetZone = null
                    dnd.currentDropMode = DropTargetMode.NONE
                }
                // On consomme l'événement pour que les cases ne réagissent pas pendant le glissement
                change.consume()
            }
        }
    }
}

/**
 * Pendant un glisser-déposer : rester 0,7 s sur un bouton de page (◀ ▶ de la grille, ▲ ▼ des colonnes)
 * tourne la page, pour déplacer un picto vers une autre page.
 * @param pageButtons zones des boutons de page et action à faire, lues à chaque instant
 */
@Composable
fun DragPageTurns(dnd: DragAndDropState, haptic: HapticFeedback, pageButtons: () -> List<Pair<Rect, () -> Unit>>) {
    LaunchedEffect(dnd.isDragging) {
        var hovered = -1
        var hoverStart = 0L
        while (dnd.isDragging) {
            val position = dnd.dragOffset
            val buttons = pageButtons()
            val target = buttons.indexOfFirst { (rect, _) -> rect.contains(position) }
            val now = System.currentTimeMillis()
            if (target != hovered) {
                hovered = target; hoverStart = now
            } else if (target >= 0 && now - hoverStart > 700) {
                buttons[target].second()
                hoverStart = now
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            kotlinx.coroutines.delay(50)
        }
    }
}
