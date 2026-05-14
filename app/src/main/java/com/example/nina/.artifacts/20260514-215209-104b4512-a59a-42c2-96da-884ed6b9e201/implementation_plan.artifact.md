# Harmonisation du Drag & Drop et Transformation de Statut

L'objectif est d'harmoniser le comportement du Drag & Drop entre la grille centrale et les colonnes latérales (Core et Quick Phrases), tout en ajoutant une logique de transformation automatique du statut `isQuickPhrase` lors d'un déplacement d'une liste à l'autre.

## Proposed Changes

### [MainActivity.kt](file:///C:/Users/fabie/AndroidStudioProjects/Nina/app/src/main/java/com/example/nina/MainActivity.kt)

#### 1. Support Vertical et Changement de Statuts (Bug 1)
- Mise à jour de la détection des zones de dépôt pour inclure les LazyColumn latérales (Core et Quick).
- Mise à jour de `onDropRequest` pour transformer le statut `isQuickPhrase`.
- Contrôle du scroll des barres latérales via `userScrollEnabled = !dndState.isDragging`.

#### 2. Harmonisation du déclencheur (Bug 2)
- Ajout du déclencheur de drag sur les cellules des barres latérales (Core Words et Quick Fires) en utilisant `detectDragGesturesAfterLongPress`.
- Respect de la condition de sécurité : `if (isAdminMode && isEditMode)`.

```kotlin
// Exemple de modification de la logique de drop
val onDropRequest: (Offset, AACCell) -> Unit = { screenPos, droppedCell ->
    // ...
    if (sidebarRect.contains(screenPos)) {
        // Core Words : isQuickPhrase = false
        if (!coreWords.any { it.label == droppedCell.label }) {
            coreWords.add(droppedCell.copy(isQuickPhrase = false))
            saveCoreWords(coreWords)
        }
    } else if (quickFiresRect.contains(screenPos)) {
        // Quick Fires : isQuickPhrase = true
        if (!quickFires.any { it.label == droppedCell.label }) {
            quickFires.add(droppedCell.copy(isQuickPhrase = true))
            saveQuickFires(quickFires)
        }
    }
    // ...
}
```

## Verification Plan

### Automated Tests
- Exécution de `./gradlew assembleDebug` pour vérifier la compilation.

### Manual Verification
- **Drag depuis Core/Quick** : Vérifier qu'un appui long déclenche bien le drag et non plus l'édition.
- **Dépôt dans Quick Phrases** : Vérifier que l'icône éclair (bolt) apparaît sur le picto déposé.
- **Dépôt dans Core/Grille** : Vérifier que l'icône éclair disparaît si elle était présente.
- **Scroll** : Vérifier que les listes latérales ne scrollent plus pendant qu'un picto est traîné.
- **Verrouillage** : Vérifier que le drag est impossible si le mode édition est désactivé.
