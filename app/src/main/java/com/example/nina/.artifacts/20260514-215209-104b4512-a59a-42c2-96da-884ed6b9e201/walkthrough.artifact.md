# Nettoyage des Dépréciations et Harmonisation du Drag & Drop

J'ai effectué le nettoyage des avertissements de dépréciation, simplifié la logique de navigation et harmonisé le comportement du Drag & Drop entre la grille et les barres latérales.

## Changements effectués

### 1. Harmonisation et Rectification du Drag & Drop (`MainActivity.kt`)
- **Déclencheurs (Sidebars & Grille)** : Les cellules des colonnes "Core Words", "Quick Phrases" et la "Grille Centrale" ont été harmonisées.
- **Logique de Verrouillage** : Le Drag & Drop est désormais lié uniquement à l'ouverture du cadenas (`isAdminMode`). La dépendance au mode édition (`isEditMode`) a été supprimée.
- **Sécurité** :
    - Le Drag est autorisé si `isAdminMode == true`.
    - L'ouverture du dialogue d'édition reste exclusive aux boutons "crayon" en mode édition, et n'est plus déclenchée par un appui long.

### 2. Mise à jour des composants Material 3 (`Dialogs.kt`)
- **TopAppBar** : Passage de `centerAlignedTopAppBarColors` à `topAppBarColors`.
- **Onglets** : Remplacement de `TabRow` par `PrimaryTabRow` et mise à jour de l'indicateur.
- **Icône Volume** : Utilisation de `Icons.AutoMirrored.Filled.VolumeUp`.

### 3. Correction de l'API MediaRecorder (`Utils.kt`)
- Remplacement du constructeur déprécié `MediaRecorder()` par `MediaRecorder(context)`.

## Vérification
- Compilation réussie via `./gradlew app:assembleDebug`.
- Validation de la logique de transformation de statut dans `onDropRequest`.
- Validation de la désactivation du scroll pendant le drag.
