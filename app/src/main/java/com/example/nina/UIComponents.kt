package com.example.nina

import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Barre de phrase en haut de l'écran (comme la "message window" de TD Snap / Grid 3) :
 * toucher la barre lit la phrase, ⌫ efface le dernier mot, la poubelle efface tout.
 */
@Composable
fun SentenceBar(
    sentence: SnapshotStateList<AACCell>,
    onSpeak: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
    isMuted: Boolean = false, // Volume multimédia à zéro : alerte visible pour l'adulte
    onMutedClick: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    // Le dernier mot ajouté reste visible quand la phrase est longue
    LaunchedEffect(sentence.size) { if (sentence.isNotEmpty()) listState.animateScrollToItem(sentence.lastIndex) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
        shape = RoundedCornerShape(50.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = sentence.isNotEmpty()) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSpeak()
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (sentence.isEmpty()) Text(
                    stringResource(R.string.hint_nina_speaks),
                    color = Color.LightGray,
                    fontSize = 20.sp,
                )
                else LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(sentence) { _, cell -> SentenceItem(cell) }
                }
            }
            if (isMuted) {
                // Son coupé : Nina ne serait pas entendue. Toucher l'alerte ouvre le réglage du volume.
                Row(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFFC62828))
                        .clickable { onMutedClick() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Son coupé", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            if (sentence.isNotEmpty()) {
                // Effacer le dernier mot
                IconButton(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onBackspace() },
                    modifier = Modifier.padding(start = 8.dp).size(64.dp).background(Color(0xFFEDEFF7), CircleShape)
                        .gazeZone("effacer_mot", onActivate = onBackspace)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Effacer le dernier mot", tint = Color(0xFF4A5D91), modifier = Modifier.size(32.dp))
                }
                // Effacer toute la phrase
                IconButton(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClear() },
                    modifier = Modifier.padding(start = 12.dp).size(55.dp).background(Color(0xFFFFEBEE), CircleShape)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Effacer la phrase", tint = Color.Red)
                }
            }
        }
    }
}

/**
 * Un mot de la barre de phrase : l'image avec le texte en dessous (aide à la lecture).
 * Sans image (mot tapé au clavier), seul le texte est affiché.
 */
@Composable
private fun SentenceItem(cell: AACCell) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .height(84.dp)
            .widthIn(min = 70.dp)
            .clip(shape)
            .background(Color(cell.colorLong).copy(alpha = 0.25f))
            .border(1.dp, Color.LightGray, shape)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        if (!cell.imageUrl.isNullOrEmpty() && cell.imageUrl != "FETCH") {
            AsyncImage(
                model = cell.imageUrl, contentDescription = cell.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(58.dp)
            )
            Text(
                cell.label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, color = Color.DarkGray, textAlign = TextAlign.Center
            )
        } else {
            Text(
                cell.label, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, color = Color.DarkGray, textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Bouton principal représentant un pictogramme ou un dossier.
 * Gère l'affichage, les clics, et l'anti-tremblement.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AACCellView(
    cell: AACCell,
    haptic: HapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current,
    debounceTime: Long = 500L,
    isAdminMode: Boolean = false,
    isEditMode: Boolean = false,
    isCviMode: Boolean = false,
    isGuided: Boolean = false, // Case à toucher pendant la modélisation (chemin vers un mot)
    // Taille de la case : carrée par défaut (colonnes), imposée par la grille paginée
    modifier: Modifier = Modifier.aspectRatio(1f),
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    // Si le bouton est masqué et qu'on n'est pas en mode Admin, on ne l'affiche pas (sa place reste vide)
    if (cell.isHidden && !isAdminMode) {
        Box(modifier = modifier.padding(2.dp))
        return
    }

    val alpha = if (cell.isHidden) 0.4f else 1f
    val cardBgColor = if (isCviMode) Color.Black else Color(cell.colorLong)
    val cardBorder = if (isCviMode) BorderStroke(4.dp, Color.Yellow) else if (cell.isFolder) BorderStroke(2.dp, Color(0xFFC4A77D).copy(alpha = 0.6f)) else null

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f)
    val folderShape = remember { FolderTabShape() }

    val dndState = LocalDragAndDropState.current
    val isTarget = dndState.currentTargetCell == cell
    val dropMode = dndState.currentDropMode

    val visualFeedbackModifier = if (isTarget) {
        when (dropMode) {
            DropTargetMode.SWAP -> Modifier.border(4.dp, Color.Blue, RoundedCornerShape(12.dp))
            DropTargetMode.INSERT_BEFORE -> Modifier.drawWithContent {
                drawContent()
                drawLine(
                    color = Color.Blue,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 16f
                )
            }
            DropTargetMode.INSERT_AFTER -> Modifier.drawWithContent {
                drawContent()
                drawLine(
                    color = Color.Blue,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 16f
                )
            }
            else -> Modifier
        }
    } else Modifier

    // Modélisation : cadre orange qui clignote pour montrer où toucher.
    // Pas d'agrandissement : la case dépasserait des bords de la grille et serait rognée.
    val guideModifier = if (isGuided) {
        val transition = rememberInfiniteTransition(label = "guide")
        val pulse by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "guidePulse"
        )
        Modifier.border(8.dp, Color(0xFFFF6D00).copy(alpha = 0.25f + pulse * 0.75f), RoundedCornerShape(16.dp))
    } else Modifier

    Box(
        modifier = modifier
            .padding(if (cell.isFolder) 2.dp else 0.dp)
            .alpha(alpha)
            .then(visualFeedbackModifier)
            .then(guideModifier)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = {
                        // Gestion de l'anti-tremblement (debounce)
                        val now = System.currentTimeMillis()
                        if ((now - lastClickTime) > debounceTime) {
                            lastClickTime = now
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick()
                        }
                    },
                    onLongClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick() 
                    }
                ),
            shape = if (cell.isFolder) folderShape else RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            elevation = CardDefaults.cardElevation(if (cell.isFolder) 6.dp else 2.dp),
            border = cardBorder
        ) {
            // Contenu visuel du bouton (image + texte)
            AACCellContent(cell, isCviMode, isEditMode)
        }
    }
}

/** Disposition calculée de la grille paginée. */
data class GridLayout(val rows: Int, val perPage: Int, val pageCount: Int)

/**
 * Calcule combien de rangées tiennent dans la hauteur disponible, pour `columns` pictos par rangée.
 * Une case garde au moins 65 % de sa largeur en hauteur ; au-delà de ce qui tient, on passe en pages.
 * Fonction pure (testée sans Android), en dp.
 */
fun computeGridLayout(itemCount: Int, columns: Int, width: Float, height: Float, gap: Float, pagerHeight: Float): GridLayout {
    val cellWidth = (width - gap * (columns - 1)) / columns
    fun rowsFor(h: Float) = ((h + gap) / (cellWidth * 0.65f + gap)).toInt().coerceAtLeast(1)

    val rowsAlone = rowsFor(height)
    if (itemCount <= columns * rowsAlone) return GridLayout(rowsAlone, columns * rowsAlone, 1)

    // Trop de pictos : on réserve la place des boutons de page
    val rows = rowsFor(height - pagerHeight - gap)
    val perPage = columns * rows
    return GridLayout(rows, perPage, (itemCount + perPage - 1) / perPage)
}

/**
 * Grille sans défilement : les pictos remplissent l'écran, rangée par rangée.
 * S'il y en a trop, des boutons Page ◀ ▶ apparaissent en bas (comme les pages de TD Snap / Grid 3).
 * Les cases vides de la dernière page gardent leur place pour que les pictos ne bougent pas.
 */
@Composable
fun PagedGrid(
    cells: List<AACCell>,
    columns: Int,
    page: Int,
    onPageChange: (Int) -> Unit,
    onLayout: (GridLayout) -> Unit,
    onPagerBounds: (previous: Rect, next: Rect) -> Unit,
    modifier: Modifier = Modifier,
    onSpeak: (String) -> Unit = {}, // Lecture vocale des boutons de page
    gazeKey: String = "pages", // Préfixe des zones regardables des boutons de page
    gap: Dp = 16.dp, // Espace entre les cases (plus grand pour la commande oculaire)
    cellContent: @Composable (cell: AACCell, modifier: Modifier) -> Unit
) {
    val pagerHeight = 72.dp
    BoxWithConstraints(modifier = modifier) {
        val layout = computeGridLayout(cells.size, columns, maxWidth.value, maxHeight.value, gap.value, pagerHeight.value)
        SideEffect { onLayout(layout) }
        val currentPage = page.coerceIn(0, layout.pageCount - 1)
        if (currentPage != page) SideEffect { onPageChange(currentPage) }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                repeat(layout.rows) { row ->
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(columns) { column ->
                            val index = currentPage * layout.perPage + row * columns + column
                            val cell = cells.getOrNull(index)
                            if (cell != null) key(cell.id, index) { cellContent(cell, Modifier.weight(1f).fillMaxHeight()) }
                            else Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (layout.pageCount > 1) {
                var previousBounds by remember { mutableStateOf(Rect.Zero) }
                var nextBounds by remember { mutableStateOf(Rect.Zero) }
                DisposableEffect(Unit) { onDispose { onPagerBounds(Rect.Zero, Rect.Zero) } }
                SideEffect { onPagerBounds(previousBounds, nextBounds) }
                Row(
                    modifier = Modifier.fillMaxWidth().height(pagerHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    val previousPage: () -> Unit = { if (currentPage > 0) { onPageChange(currentPage - 1); onSpeak("Page précédente") } }
                    PageButton(
                        forward = false, enabled = currentPage > 0,
                        modifier = Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { previousBounds = it.boundsInRoot() }
                            .gazeZone("${gazeKey}_precedente", enabled = currentPage > 0, onActivate = previousPage)
                    ) { previousPage() }
                    Text(
                        "Page ${currentPage + 1} / ${layout.pageCount}",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A5D91),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    val nextPage: () -> Unit = { if (currentPage < layout.pageCount - 1) { onPageChange(currentPage + 1); onSpeak("Page suivante") } }
                    PageButton(
                        forward = true, enabled = currentPage < layout.pageCount - 1,
                        modifier = Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { nextBounds = it.boundsInRoot() }
                            .gazeZone("${gazeKey}_suivante", enabled = currentPage < layout.pageCount - 1, onActivate = nextPage)
                    ) { nextPage() }
                }
            }
        }
    }
}

@Composable
private fun PageButton(forward: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5D91), disabledContainerColor = Color(0xFFE6E6EE))
    ) {
        Icon(
            if (forward) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = if (forward) "Page suivante" else "Page précédente",
            modifier = Modifier.size(36.dp)
        )
    }
}

/**
 * Combien de cases tiennent dans une colonne latérale de hauteur `height` (dp),
 * chaque case faisant au moins `minItemHeight`. Au-delà, on passe en pages ▲ ▼.
 * Fonction pure (testée sans Android).
 */
fun computeColumnLayout(itemCount: Int, height: Float, minItemHeight: Float, gap: Float, pagerHeight: Float): GridLayout {
    fun rowsFor(h: Float) = ((h + gap) / (minItemHeight + gap)).toInt().coerceAtLeast(1)
    val rowsAlone = rowsFor(height)
    if (itemCount <= rowsAlone) return GridLayout(rowsAlone, rowsAlone, 1)
    val rows = rowsFor(height - pagerHeight - gap)
    return GridLayout(rows, rows, (itemCount + rows - 1) / rows)
}

/**
 * Colonne latérale sans défilement (dossiers à gauche, phrases rapides à droite) :
 * les cases se partagent la hauteur ; s'il y en a trop, des boutons ▲ ▼ changent de page.
 * Les places vides gardent leur taille pour que les cases ne bougent pas.
 */
@Composable
fun PagedColumn(
    itemCount: Int,
    page: Int,
    onPageChange: (Int) -> Unit,
    onLayout: (GridLayout) -> Unit,
    onPagerBounds: (previous: Rect, next: Rect) -> Unit,
    modifier: Modifier = Modifier,
    onSpeak: (String) -> Unit = {}, // Lecture vocale des boutons de page
    gazeKey: String = "pages", // Préfixe des zones regardables des boutons de page
    minItemHeight: Dp = 80.dp,
    gap: Dp = 12.dp,
    item: @Composable (index: Int, modifier: Modifier) -> Unit
) {
    val pagerHeight = 52.dp
    BoxWithConstraints(modifier = modifier) {
        val layout = computeColumnLayout(itemCount, maxHeight.value, minItemHeight.value, gap.value, pagerHeight.value)
        SideEffect { onLayout(layout) }
        val currentPage = page.coerceIn(0, layout.pageCount - 1)
        if (currentPage != page) SideEffect { onPageChange(currentPage) }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(layout.rows) { row ->
                val index = currentPage * layout.perPage + row
                if (index < itemCount) key(index) { item(index, Modifier.weight(1f).fillMaxWidth()) }
                else Spacer(modifier = Modifier.weight(1f))
            }
            if (layout.pageCount > 1) {
                var previousBounds by remember { mutableStateOf(Rect.Zero) }
                var nextBounds by remember { mutableStateOf(Rect.Zero) }
                DisposableEffect(Unit) { onDispose { onPagerBounds(Rect.Zero, Rect.Zero) } }
                SideEffect { onPagerBounds(previousBounds, nextBounds) }
                Row(modifier = Modifier.fillMaxWidth().height(pagerHeight), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val previousPage: () -> Unit = { if (currentPage > 0) { onPageChange(currentPage - 1); onSpeak("Page précédente") } }
                    ColumnPageButton(up = true, enabled = currentPage > 0,
                        modifier = Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { previousBounds = it.boundsInRoot() }
                            .gazeZone("${gazeKey}_precedente", enabled = currentPage > 0, onActivate = previousPage)
                    ) { previousPage() }
                    val nextPage: () -> Unit = { if (currentPage < layout.pageCount - 1) { onPageChange(currentPage + 1); onSpeak("Page suivante") } }
                    ColumnPageButton(up = false, enabled = currentPage < layout.pageCount - 1,
                        modifier = Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { nextBounds = it.boundsInRoot() }
                            .gazeZone("${gazeKey}_suivante", enabled = currentPage < layout.pageCount - 1, onActivate = nextPage)
                    ) { nextPage() }
                }
            }
        }
    }
}

@Composable
private fun ColumnPageButton(up: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5D91), disabledContainerColor = Color(0xFFE6E6EE))
    ) {
        Icon(
            if (up) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (up) "Dossiers précédents" else "Dossiers suivants",
            modifier = Modifier.size(32.dp)
        )
    }
}

/**
 * Case 🏠 Accueil, toujours en haut de la colonne des dossiers : un toucher ramène à la grille d'accueil.
 * Elle est mise en évidence quand on est déjà à l'accueil.
 */
@Composable
fun HomeCell(isHome: Boolean, isGuided: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = Color(0xFF4A5D91)
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isHome) accent else Color.White),
        border = BorderStroke(if (isGuided) 6.dp else 2.dp, if (isGuided) Color(0xFFFF6D00) else accent)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Home, contentDescription = null, tint = if (isHome) Color.White else accent, modifier = Modifier.size(36.dp))
            Text("ACCUEIL", fontWeight = FontWeight.Black, fontSize = 13.sp, color = if (isHome) Color.White else accent)
        }
    }
}

/**
 * LOGIQUE DRAG & DROP RECONSTRUITE
 */

enum class DropTargetMode { NONE, SWAP, INSERT_BEFORE, INSERT_AFTER }

class DragAndDropState {
    var isDragging by mutableStateOf(value = false)
    var dragOffset by mutableStateOf(value = Offset.Zero)
    var draggedCell by mutableStateOf<AACCell?>(value = null)
    var currentTargetCell by mutableStateOf<AACCell?>(null)
    var currentDropMode by mutableStateOf(DropTargetMode.NONE)
}

val LocalDragAndDropState = compositionLocalOf { DragAndDropState() }

@Composable
fun DragAndDropRoot(content: @Composable () -> Unit) {
    val state = remember { DragAndDropState() }
    
    // LOG DE RECOMPOSITION DU PARENT
    SideEffect { Log.w("DragDrop-Trace", "🔄 RECOMPOSITION : DragAndDropRoot") }

    CompositionLocalProvider(LocalDragAndDropState provides state) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            if (state.isDragging && state.draggedCell != null) {
                Box(
                    modifier = Modifier
                        .zIndex(100f) // <-- CRUCIAL : Force le fantôme à passer au-dessus du Scaffold
                        .offset { IntOffset(state.dragOffset.x.toInt() - 75, state.dragOffset.y.toInt() - 75) }
                        .size(150.dp) // Assure-toi que la taille correspond bien à tes pictos
                        .graphicsLayer { 
                            alpha = 0.9f 
                            scaleX = 1.05f 
                            scaleY = 1.05f 
                            shadowElevation = 30f // Ajoute une grosse ombre pour bien le détacher du fond
                        }
                ) {
                    AACCellView(
                        cell = state.draggedCell!!,
                        onClick = {},
                        onLongClick = {},
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraggableCellWrapper(
    cell: AACCell,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = LocalDragAndDropState.current

    Box(
        modifier = modifier
            .graphicsLayer {
                // LECTURE DIFFÉRÉE (Draw Phase) : Zéro recomposition de l'arbre UI !
                alpha = if (state.isDragging && (state.draggedCell == cell)) 0f else 1f
            }
    ) {
        content()
    }
}

/**
 * Extrait la partie visuelle d'une cellule pour la réutiliser (ex: image fantôme).
 */
@Composable
fun AACCellContent(cell: AACCell, isCviMode: Boolean = false, isEditMode: Boolean = false) {
    val textColor = if (isCviMode) Color.Yellow else Color.Black

    // Le texte grandit avec la case (de 8 pictos par rangée à 1 seul)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val labelSize = (maxHeight.value / 11f).coerceIn(12f, 32f).sp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .padding(top = if (cell.isFolder) 12.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(4.dp)
        ) {
            // Image du pictogramme (chargée via Coil)
            if (!cell.imageUrl.isNullOrEmpty()) AsyncImage(
                cell.imageUrl,
                null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            // Icône par défaut pour les dossiers sans image
            else if (cell.isFolder) Text(
                "📂",
                fontSize = 40.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            
            // Badge pour les phrases rapides (QuickFires)
            if (cell.isQuickPhrase) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = "Phrase rapide",
                    tint = Color(0xFFFFB300),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(Color.White, CircleShape)
                )
            }

            // Badge pour les boutons masqués (Admin uniquement)
            if (cell.isHidden) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = "Bouton masqué",
                    tint = Color.Red,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                        .padding(2.dp)
                )
            }

            // Icône d'édition (crayon) visible uniquement en mode édition
            if (isEditMode) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Mode édition",
                    tint = Color(0xFF6200EE),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(Color.White.copy(alpha = 0.9f), CircleShape)
                        .border(1.dp, Color(0xFF6200EE), CircleShape)
                        .padding(4.dp)
                )
            }
        }
        // Libellé du bouton
        Text(
            cell.label.uppercase(),
            fontWeight = FontWeight.Black,
            fontSize = labelSize,
            maxLines = 2,
            lineHeight = labelSize,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    }
}

/**
 * Forme personnalisée pour simuler un onglet de dossier.
 */
class FolderTabShape(
    val tabWidthDp: Dp = 60.dp,
    val tabHeightDp: Dp = 12.dp,
    val cornerRadiusDp: Dp = 8.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): Outline {
        val tw = with(density) { tabWidthDp.toPx() }
        val th = with(density) { tabHeightDp.toPx() }
        val cr = with(density) { cornerRadiusDp.toPx() }
        val path = Path().apply {
            moveTo(0f, th + cr); quadraticTo(0f, th, cr, th)
            lineTo((size.width / 2f) - (tw / 2f) - cr, th); quadraticTo(
            (size.width / 2f) - (tw / 2f),
            th,
            (size.width / 2f) - (tw / 2f),
            th / 2f
        ); quadraticTo((size.width / 2f) - (tw / 2f), 0f, (size.width / 2f) - (tw / 2f) + cr, 0f)
            lineTo((size.width / 2f) + (tw / 2f) - cr, 0f); quadraticTo(
            (size.width / 2f) + (tw / 2f),
            0f,
            (size.width / 2f) + (tw / 2f),
            th / 2f
        ); quadraticTo((size.width / 2f) + (tw / 2f), th, (size.width / 2f) + (tw / 2f) + cr, th)
            lineTo(size.width - cr, th); quadraticTo(size.width, th, size.width, th + cr)
            lineTo(size.width, size.height - cr); quadraticTo(
            size.width,
            size.height,
            size.width - cr,
            size.height
        )
            lineTo(cr, size.height); quadraticTo(0f, size.height, 0f, size.height - cr); close()
        }
        return Outline.Generic(path)
    }
}
