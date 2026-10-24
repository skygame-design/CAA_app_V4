package com.example.nina

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VisibilityOff
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
 * Composant de la barre de phrase en haut de l'écran.
 * Affiche les pictogrammes sélectionnés et permet de les supprimer.
 */
@Composable
fun SentenceBar(sentence: SnapshotStateList<AACCell>, onClear: () -> Unit, haptic: HapticFeedback) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        shape = RoundedCornerShape(50.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (sentence.isEmpty()) Text(
                    stringResource(R.string.hint_nina_speaks),
                    color = Color.LightGray,
                    fontSize = 20.sp,
                )
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(sentence) { index, cell ->
                        // Chaque picto dans la barre peut être supprimé d'un simple clic
                        SentenceItem(cell) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (index < sentence.size) sentence.removeAt(index)
                        }
                    }
                }
            }
            // Bouton de suppression totale de la phrase
            if (sentence.isNotEmpty()) IconButton(
                onClick = {
                    haptic.performHapticFeedback(
                        HapticFeedbackType.LongPress
                    ); onClear()
                }, modifier = Modifier
                    .size(55.dp)
                    .background(Color(0xFFFFEBEE), CircleShape)
            ) {
                Icon(
                    Icons.Default.Delete,
                    null,
                    tint = Color.Red
                )
            }
        }
    }
}

/**
 * Un mot de la barre de phrase : l'image avec le texte en dessous (aide à la lecture).
 * Sans image (mot tapé au clavier), seul le texte est affiché.
 */
@Composable
private fun SentenceItem(cell: AACCell, onRemove: () -> Unit) {
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
            .clickable(onClick = onRemove)
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
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    // Si le bouton est masqué et qu'on n'est pas en mode Admin, on ne l'affiche pas
    if (cell.isHidden && !isAdminMode) {
        Box(modifier = Modifier.aspectRatio(1f).padding(2.dp))
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

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(if (cell.isFolder) 2.dp else 0.dp)
            .alpha(alpha)
            .then(visualFeedbackModifier)
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
    content: @Composable () -> Unit
) {
    val state = LocalDragAndDropState.current
    
    Box(
        modifier = Modifier
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
            fontSize = 12.sp,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
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
