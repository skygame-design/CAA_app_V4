package com.example.nina

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/** Liste modifiée par un dépôt (pour savoir quoi enregistrer). */
enum class DropOutcome { CORE, QUICK, GRID, NONE }

/**
 * Applique un dépôt. Fonction pure, testée sans Android.
 * - Lâché sur la colonne de gauche ou les phrases rapides : une copie y est ajoutée (si le mot n'y est pas déjà).
 * - Lâché sur une case de la grille affichée (accueil ou dossier ouvert) : échange, ou insertion avant / après.
 */
fun applyDrop(
    dropped: AACCell, target: AACCell?, mode: DropTargetMode,
    onCoreColumn: Boolean, onQuickColumn: Boolean,
    coreWords: MutableList<AACCell>, quickFires: MutableList<AACCell>, grid: MutableList<AACCell>
): DropOutcome {
    if (onCoreColumn) {
        if (coreWords.any { it.label == dropped.label }) return DropOutcome.NONE
        coreWords.add(dropped.copy(id = newCellId(), isQuickPhrase = false)) // Copie : nouvel id
        return DropOutcome.CORE
    }
    if (onQuickColumn) {
        if (quickFires.any { it.label == dropped.label }) return DropOutcome.NONE
        quickFires.add(dropped.copy(id = newCellId(), isQuickPhrase = true)) // Copie : nouvel id
        return DropOutcome.QUICK
    }
    if (target == null || target == dropped) return DropOutcome.NONE
    val draggedIndex = grid.indexOfCell(dropped)
    val targetIndex = grid.indexOfCell(target)
    if (draggedIndex == -1 || targetIndex == -1) return DropOutcome.NONE
    when (mode) {
        DropTargetMode.SWAP -> {
            val temp = grid[draggedIndex]
            grid[draggedIndex] = grid[targetIndex]
            grid[targetIndex] = temp
        }
        DropTargetMode.INSERT_BEFORE, DropTargetMode.INSERT_AFTER -> {
            grid.removeAt(draggedIndex)
            var newTargetIndex = grid.indexOfCell(target)
            if (mode == DropTargetMode.INSERT_AFTER) newTargetIndex += 1
            grid.add(newTargetIndex, dropped.copy(isQuickPhrase = false))
        }
        else -> return DropOutcome.NONE
    }
    return DropOutcome.GRID
}

/** Mode de dépôt selon l'endroit de la case survolée : bord gauche = avant, bord droit = après, milieu = échange. */
fun dropModeAt(relativeX: Float, cellWidth: Float): DropTargetMode = when {
    relativeX < cellWidth * 0.25f -> DropTargetMode.INSERT_BEFORE
    relativeX > cellWidth * 0.75f -> DropTargetMode.INSERT_AFTER
    else -> DropTargetMode.SWAP
}

/**
 * Suivi du doigt sur tout l'écran pendant un glisser-déposer (lancé par un appui long dans une case).
 * Créé une seule fois : gridRect et onDrop sont lus à chaque événement, pour toujours avoir la version à jour
 * (sinon le dépôt travaillait sur la liste de l'accueil même dans un dossier ouvert).
 */
fun Modifier.dragAndDropTracking(
    dnd: DragAndDropState,
    gridRect: () -> Rect,
    cellBounds: Map<AACCell, Rect>,
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
                // Case survolée et mode de dépôt, en temps réel (pour le retour visuel)
                val origin = gridRect().topLeft
                val local = dnd.dragOffset - origin
                val hovered = cellBounds.entries.firstOrNull { (_, bounds) -> bounds.translate(-origin).contains(local) }
                if (hovered != null && hovered.key != dnd.draggedCell) {
                    val rect = hovered.value.translate(-origin)
                    dnd.currentTargetCell = hovered.key
                    dnd.currentDropMode = dropModeAt(local.x - rect.left, rect.width)
                } else {
                    dnd.currentTargetCell = null
                    dnd.currentDropMode = DropTargetMode.NONE
                }
                // On consomme l'événement pour que la grille ne réagisse pas pendant le glissement
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
