package com.example.nina

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Modèle de données pour un bouton AAC.
 * Utilise SnapshotStateList pour que Compose détecte les ajouts à l'intérieur des dossiers.
 */
data class AACCell(
    val label: String,
    val colorLong: Long,
    val imageUrl: String? = null,
    val audioPath: String? = null,
    val isFolder: Boolean = false,
    val imageRes: Int? = null,
    val content: SnapshotStateList<AACCell> = mutableStateListOf()

)

