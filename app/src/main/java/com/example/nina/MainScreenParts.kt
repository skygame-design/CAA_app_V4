package com.example.nina

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Blocs de l'écran principal : barre du haut, bandeau de modélisation, colonne des dossiers,
 * grille centrale, phrases rapides. L'état reste dans MainActivity ; ces blocs l'affichent
 * et remontent les actions.
 */

/** Ce qu'il faut pour afficher et faire réagir une case, identique dans les trois zones. */
class CellUi(
    val haptic: HapticFeedback,
    val debounceTime: Long,
    val isAdminMode: Boolean,
    val isEditMode: Boolean,
    val isCviMode: Boolean,
    val guidedId: String?,
    val dnd: DragAndDropState
)

/** Pagination d'une zone : page affichée, disposition calculée, zones des boutons de page. */
class PagingUi(
    val page: Int,
    val onPageChange: (Int) -> Unit,
    val onLayout: (GridLayout) -> Unit,
    val onPagerBounds: (previous: Rect, next: Rect) -> Unit
)

/**
 * Une case de picto : toucher ou regard (fixation) l'active ; appui long en mode adulte = glisser-déposer,
 * sinon l'action donnée (formes du mot).
 */
@Composable
fun InteractiveCell(
    cell: AACCell, ui: CellUi, zone: DropZone, gazeKey: String, modifier: Modifier,
    onClick: () -> Unit,
    onChildLongPress: (() -> Unit)? = null,
    highlighted: Boolean = false, // Dossier ouvert : encadré, l'enfant voit où il se trouve
    onBounds: ((Rect) -> Unit)? = null
) {
    var posInRoot by remember { mutableStateOf(Offset.Zero) }
    DraggableCellWrapper(cell = cell, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (highlighted) Modifier.border(4.dp, Color(0xFF4A5D91), RoundedCornerShape(22.dp)).padding(4.dp) else Modifier)
                .onGloballyPositioned {
                    posInRoot = it.positionInRoot()
                    onBounds?.invoke(it.boundsInRoot())
                }
                .gazeZone(gazeKey, enabled = !(cell.isHidden && !ui.isAdminMode), onActivate = onClick)
        ) {
            AACCellView(
                cell = cell, haptic = ui.haptic, debounceTime = ui.debounceTime,
                isAdminMode = ui.isAdminMode, isEditMode = ui.isEditMode, isCviMode = ui.isCviMode, isGuided = cell.id == ui.guidedId,
                modifier = Modifier.fillMaxSize(),
                onClick = onClick,
                onLongClick = {
                    if (ui.isAdminMode) {
                        ui.dnd.sourceZone = zone
                        ui.dnd.draggedCell = cell
                        ui.dnd.dragOffset = posInRoot
                        ui.dnd.isDragging = true
                    } else {
                        onChildLongPress?.invoke()
                    }
                }
            )
        }
    }
}

/** Barre du haut : bouton Lire, barre de phrase, outils discrets de l'adulte, bouton Retour (sous-dossier). */
@Composable
fun TopBar(
    phrase: SnapshotStateList<AACCell>, haptic: HapticFeedback, isMuted: Boolean,
    onSpeak: () -> Unit, onMutedClick: () -> Unit, onWordFinder: () -> Unit, onKeyboard: () -> Unit,
    showBack: Boolean, onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bouton Play : prononce la phrase construite
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSpeak()
            },
            enabled = phrase.isNotEmpty(),
            modifier = Modifier
                .size(90.dp)
                .gazeZone("lire", enabled = phrase.isNotEmpty(), onActivate = onSpeak)
                .background(if (phrase.isNotEmpty()) Color(0xFF4CAF50) else Color(0xFFE0E0E0), CircleShape)
                .border(4.dp, Color.White, CircleShape)
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(60.dp))
        }

        SentenceBar(
            sentence = phrase,
            onSpeak = onSpeak,
            onBackspace = { if (phrase.isNotEmpty()) phrase.removeAt(phrase.lastIndex) },
            onClear = { phrase.clear() },
            haptic = haptic,
            isMuted = isMuted,
            onMutedClick = onMutedClick,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
        )

        // Outils de l'adulte (recherche / modélisation, saisie libre) : à l'écart de la phrase, en gris discret
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onWordFinder) {
                Icon(Icons.Default.Search, contentDescription = "Chercher un mot", tint = Color.Gray)
            }
            IconButton(onClick = onKeyboard) {
                Icon(Icons.Default.Keyboard, contentDescription = "Saisie libre", tint = Color.Gray)
            }
        }

        // Bouton Retour : seulement dans un sous-dossier (au premier niveau, 🏠 Accueil suffit)
        Box(modifier = Modifier.width(80.dp)) {
            if (showBack) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(70.dp)
                        .gazeZone("retour", onActivate = onBack)
                        .background(Color.White, CircleShape)
                        .border(3.dp, Color(0xFF6200EE), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF6200EE), modifier = Modifier.size(35.dp))
                }
            }
        }
    }
}

/** Bandeau de modélisation : la case orange à toucher pour atteindre le mot cherché. */
@Composable
fun GuideBanner(targetLabel: String, step: Int, total: Int, onStop: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .background(Color(0xFFFFF3E0), RoundedCornerShape(16.dp))
            .border(2.dp, Color(0xFFFF6D00), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFFFF6D00))
        Text(
            "Touche la case orange pour dire « $targetLabel »  (${step + 1}/$total)",
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5D4037),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        TextButton(onClick = onStop) { Text("Arrêter") }
    }
}

/** Colonne de gauche : 🏠 Accueil (fixe) puis les dossiers, sans défilement (pages ▲ ▼ si besoin). */
@Composable
fun FolderColumn(
    cells: List<AACCell>, openFolderId: String?, isHome: Boolean, ui: CellUi, paging: PagingUi,
    minItemHeight: Dp, gap: Dp, onSpeak: (String) -> Unit, cellBounds: MutableMap<AACCell, Rect>,
    onHome: () -> Unit, onCellClick: (AACCell) -> Unit, onCellLongPress: (AACCell) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeCell(
            isHome = isHome,
            isGuided = false,
            onClick = onHome,
            modifier = Modifier.fillMaxWidth().height(80.dp).gazeZone("accueil", onActivate = onHome)
        )
        // Pas de case « + » : la colonne reste identique en mode enfant et adulte
        PagedColumn(
            itemCount = cells.size,
            page = paging.page,
            onPageChange = paging.onPageChange,
            onLayout = paging.onLayout,
            onSpeak = onSpeak,
            gazeKey = "pages_gauche",
            minItemHeight = minItemHeight,
            gap = gap,
            onPagerBounds = paging.onPagerBounds,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { index, slotModifier ->
            val cell = cells[index]
            DisposableEffect(cell) { onDispose { cellBounds.remove(cell) } }
            InteractiveCell(
                cell, ui, DropZone.CORE, "gauche_${cell.id}", slotModifier,
                onClick = { onCellClick(cell) },
                onChildLongPress = { onCellLongPress(cell) },
                highlighted = cell.isFolder && openFolderId == cell.id,
                onBounds = { cellBounds[cell] = it }
            )
        }
    }
}

/** Grille centrale (mots essentiels à l'accueil, ou contenu du dossier ouvert), sans défilement. */
@Composable
fun CenterGrid(
    cells: SnapshotStateList<AACCell>, columns: Int, gap: Dp, ui: CellUi, paging: PagingUi,
    onSpeak: (String) -> Unit, cellBounds: MutableMap<AACCell, Rect>,
    onCellClick: (AACCell) -> Unit, onCellLongPress: (AACCell) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = cells,
        transitionSpec = {
            (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
        },
        label = "GridAnimation"
    ) { targetButtons ->
        // Grille sans défilement : de 1 à 8 pictos par rangée, des pages si tout ne tient pas
        PagedGrid(
            cells = targetButtons,
            columns = columns,
            gap = gap,
            page = paging.page,
            onPageChange = paging.onPageChange,
            onLayout = paging.onLayout,
            onSpeak = onSpeak,
            gazeKey = "pages_grille",
            onPagerBounds = paging.onPagerBounds,
            modifier = modifier
        ) { cell, cellModifier ->
            // Quand la case quitte l'écran (changement de page ou de dossier), on oublie sa position
            DisposableEffect(cell) {
                onDispose { cellBounds.remove(cell) }
            }
            InteractiveCell(
                cell, ui, DropZone.GRID, "grille_${cell.id}", cellModifier,
                onClick = { onCellClick(cell) },
                onChildLongPress = { onCellLongPress(cell) },
                onBounds = { cellBounds[cell] = it }
            )
        }
    }
}

/** Colonne de droite : cadenas discret, puis les phrases rapides (pages ▲ ▼ si besoin). */
@Composable
fun QuickPhrasesColumn(
    cells: List<AACCell>, ui: CellUi, paging: PagingUi, minItemHeight: Dp, gap: Dp, onSpeak: (String) -> Unit,
    cellBounds: MutableMap<AACCell, Rect>,
    lockButton: @Composable () -> Unit,
    onCellClick: (AACCell) -> Unit,
    columnModifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier.width(110.dp).fillMaxHeight().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        lockButton()
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        PagedColumn(
            itemCount = cells.size,
            page = paging.page,
            onPageChange = paging.onPageChange,
            onLayout = paging.onLayout,
            onSpeak = onSpeak,
            gazeKey = "pages_droite",
            minItemHeight = minItemHeight,
            gap = gap,
            onPagerBounds = paging.onPagerBounds,
            modifier = Modifier.weight(1f).fillMaxWidth().then(columnModifier)
        ) { index, slotModifier ->
            val cell = cells[index]
            DisposableEffect(cell) { onDispose { cellBounds.remove(cell) } }
            InteractiveCell(
                cell, ui, DropZone.QUICK, "droite_${cell.id}", slotModifier,
                onClick = { onCellClick(cell) },
                onBounds = { cellBounds[cell] = it }
            )
        }
    }
}
