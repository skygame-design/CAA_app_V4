package com.example.nina

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/*
 * Outils de l'adulte : cadenas discret (maintenir 2 s), barre du mode adulte, et ouverture des fenêtres.
 */

/**
 * Cadenas discret en haut à droite : petit, gris, sans couleur vive pour ne pas attirer l'enfant.
 * Il faut le maintenir 2 secondes (un anneau se remplit) pour ouvrir le calcul du mode adulte.
 * Un simple appui affiche seulement « Maintenir 2 s ». Cadenas ouvert, un appui le referme.
 */
@Composable
fun HoldLockButton(isAdminMode: Boolean, onHoldComplete: () -> Unit, onLock: () -> Unit, warning: Boolean = false) {
    if (isAdminMode) {
        IconButton(onClick = onLock, modifier = Modifier.size(48.dp).background(Color(0xFFFFEB3B), CircleShape)) {
            Icon(Icons.Default.LockOpen, contentDescription = "Verrouiller")
        }
        return
    }

    val scope = rememberCoroutineScope()
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(showHint) { if (showHint) { kotlinx.coroutines.delay(2000); showHint = false } }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        val hold = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, androidx.compose.animation.core.tween(2000, easing = androidx.compose.animation.core.LinearEasing))
                            onHoldComplete()
                            progress.snapTo(0f)
                        }
                        tryAwaitRelease()
                        if (hold.isActive) {
                            // Relâché trop tôt : on n'ouvre rien
                            hold.cancel()
                            scope.launch { progress.snapTo(0f) }
                            showHint = true
                        }
                    })
                }
        ) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF4A5D91),
                trackColor = Color.Transparent,
                strokeWidth = 4.dp
            )
            Icon(Icons.Default.Lock, contentDescription = "Mode adulte (maintenir 2 secondes)", tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
            // Point orange discret : un réglage demande l'attention d'un adulte (voir la barre du mode adulte)
            if (warning) Box(modifier = Modifier.align(Alignment.TopEnd).size(10.dp).background(Color(0xFFFF6D00), CircleShape))
        }
        if (showHint) Text("Maintenir 2 s", fontSize = 11.sp, color = Color.Gray)
    }
}

/**
 * Barre du mode adulte, en haut de l'écran : tous les outils au même endroit,
 * sans pousser les phrases rapides de la colonne de droite.
 */
@Composable
fun AdminToolbar(
    showMore: Boolean, onMoreToggle: (Boolean) -> Unit,
    isEditMode: Boolean, onEditModeToggle: (Boolean) -> Unit,
    columnCount: Int, onColumnCountChange: (Int) -> Unit,
    onAddToGrid: () -> Unit, onAddToLeft: () -> Unit, onAddToQuick: () -> Unit, onSettings: () -> Unit, onStats: () -> Unit, onHelp: () -> Unit,
    onCalibration: () -> Unit, isKioskMode: Boolean, onKioskToggle: () -> Unit,
    onBackup: () -> Unit, onRestore: () -> Unit,
    onRecommendedLayout: () -> Unit, onRestorePreviousLayout: (() -> Unit)?, // null : rien à restaurer
    onAutoBackups: () -> Unit,
    onEyeGaze: () -> Unit,
    onPrintBoard: () -> Unit,
    voiceWarning: String?, onVoiceFix: () -> Unit, // null : la voix fonctionne hors ligne
    onLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFF4A5D91)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF3C4))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF8D6E00))
        Text("Mode adulte", fontWeight = FontWeight.Bold, color = Color(0xFF5D4600), modifier = Modifier.padding(end = 8.dp))
        // Problème de voix : bouton orange bien visible pour l'adulte, qui explique comment installer la voix
        voiceWarning?.let { message ->
            Button(
                onClick = onVoiceFix,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(message)
            }
        }

        // Ajouter : on choisit la zone (les colonnes n'ont pas de case « + » pour ne pas changer de disposition)
        Box {
            var showAddMenu by remember { mutableStateOf(false) }
            AdminToolButton(Icons.Default.Add, "Ajouter", { showAddMenu = true })
            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                DropdownMenuItem(text = { Text("Dans la grille (dossier ouvert)") }, onClick = { showAddMenu = false; onAddToGrid() })
                DropdownMenuItem(text = { Text("Dans la colonne de gauche (dossiers)") }, onClick = { showAddMenu = false; onAddToLeft() })
                DropdownMenuItem(text = { Text("Dans les phrases rapides") }, onClick = { showAddMenu = false; onAddToQuick() })
            }
        }
        AdminToolButton(Icons.Default.Edit, if (isEditMode) "Édition : oui" else "Édition", { onEditModeToggle(!isEditMode) }, selected = isEditMode)
        AdminToolButton(Icons.Default.Settings, "Paramètres", onSettings)
        AdminToolButton(Icons.Default.BarChart, "Statistiques", onStats)
        AdminToolButton(Icons.AutoMirrored.Filled.Help, "Aide", onHelp)

        // Taille de la grille : pictos par rangée (1 à 8)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(horizontal = 4.dp)
        ) {
            IconButton(onClick = { if (columnCount > 1) onColumnCountChange(columnCount - 1) }, enabled = columnCount > 1) {
                Icon(Icons.Default.Remove, contentDescription = "Moins de pictos par rangée")
            }
            Text("$columnCount par rangée", fontWeight = FontWeight.Bold, color = accent)
            IconButton(onClick = { if (columnCount < 8) onColumnCountChange(columnCount + 1) }, enabled = columnCount < 8) {
                Icon(Icons.Default.Add, contentDescription = "Plus de pictos par rangée")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Outils plus rares
        Box {
            IconButton(onClick = { onMoreToggle(true) }) { Icon(Icons.Default.MoreVert, contentDescription = "Plus d'outils") }
            DropdownMenu(expanded = showMore, onDismissRequest = { onMoreToggle(false) }) {
                DropdownMenuItem(text = { Text("👁 Commande oculaire…") }, onClick = { onMoreToggle(false); onEyeGaze() })
                DropdownMenuItem(text = { Text("🎯 Calibrer le tactile") }, onClick = { onMoreToggle(false); onCalibration() })
                DropdownMenuItem(text = { Text(if (isKioskMode) "🔓 Désactiver le mode Kiosque" else "🔒 Activer le mode Kiosque") }, onClick = { onMoreToggle(false); onKioskToggle() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("📤 Partager / Sauvegarder") }, onClick = { onMoreToggle(false); onBackup() })
                DropdownMenuItem(text = { Text(stringResource(R.string.btn_restore)) }, onClick = { onMoreToggle(false); onRestore() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("🗂 Sauvegardes automatiques…") }, onClick = { onMoreToggle(false); onAutoBackups() })
                DropdownMenuItem(text = { Text("🧩 Grilles prêtes…") }, onClick = { onMoreToggle(false); onRecommendedLayout() })
                DropdownMenuItem(text = { Text("🖨 Planche imprimable…") }, onClick = { onMoreToggle(false); onPrintBoard() })
                onRestorePreviousLayout?.let { restore ->
                    DropdownMenuItem(text = { Text("↩ Revenir à la disposition précédente") }, onClick = { onMoreToggle(false); restore() })
                }
            }
        }

        Button(onClick = onLock, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Verrouiller")
        }
    }
}

@Composable
private fun AdminToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, selected: Boolean = false) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (selected) Color(0xFF4A5D91) else Color.White, contentColor = if (selected) Color.White else Color(0xFF4A5D91))
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
fun DialogManager(
    showKeyboardDialog: Boolean, onKeyboardDismiss: () -> Unit, onKeyboardValidate: (AACCell) -> Unit,
    showAdminChallenge: Boolean, onAdminSuccess: () -> Unit, onAdminDismiss: () -> Unit,
    showAddDialog: Boolean, onAddDismiss: () -> Unit, onAddConfirm: (AACCell) -> Unit,
    showSettings: Boolean, currentBg: Long, currentPitch: Float, currentRate: Float, currentDebounce: Long, currentCviMode: Boolean, currentAutoReturn: Boolean,
    onSettingsDismiss: () -> Unit, onSettingsSave: (Long, Float, Float, Long, Boolean, Boolean) -> Unit,
    showHelp: Boolean, onHelpDismiss: () -> Unit,
    showCalibration: Boolean, onCalibrationFinished: (Float, Float) -> Unit,
    showWordFinder: Boolean, coreWords: List<AACCell>, quickFires: List<AACCell>, onWordFinderShow: (WordGuide) -> Unit, onWordFinderDismiss: () -> Unit,
    showStats: Boolean, usageStats: Map<String, Int>, onStatsDismiss: () -> Unit, onStatsReset: () -> Unit,
    showEditDialog: Boolean, cellToEdit: AACCell?, allButtons: List<AACCell>,
    onEditDelete: (AACCell) -> Unit, onEditSave: (AACCell, Boolean, AACCell?) -> Unit, onEditDismiss: () -> Unit
) {
    if (showKeyboardDialog) KeyboardInputDialog(onDismiss = onKeyboardDismiss, onValidate = onKeyboardValidate)
    if (showAdminChallenge) AdminChallengeDialog(onDismiss = onAdminDismiss, onSuccess = onAdminSuccess)
    if (showAddDialog) AddButtonDialog(onDismiss = onAddDismiss, onConfirm = onAddConfirm)
    if (showSettings) SettingsDialog(currentBg, currentPitch, currentRate, currentDebounce, currentCviMode, currentAutoReturn, onSettingsDismiss, onSettingsSave)
    if (showHelp) HelpDialog(onDismiss = onHelpDismiss)
    if (showCalibration) CalibrationTest(onFinished = onCalibrationFinished)
    if (showWordFinder) WordFinderDialog(allButtons = allButtons, coreWords = coreWords, quickFires = quickFires, onShow = onWordFinderShow, onDismiss = onWordFinderDismiss)
    if (showStats) StatsDialog(stats = usageStats, onDismiss = onStatsDismiss, onReset = onStatsReset)
    if (showEditDialog && cellToEdit != null) EditDialog(cellToEdit, allFolders(allButtons, coreWords), onDelete = onEditDelete, onSave = onEditSave, onDismiss = onEditDismiss)
}
