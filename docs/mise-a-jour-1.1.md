# Mettre à jour Nina : de la version de développement à la version 1.1

Ce tutoriel explique comment passer une tablette de la **version de développement** de Nina (celle installée avec le bouton ▶ d'Android Studio) à la **version finale 1.1**, **sans rien perdre** : grilles, dossiers, photos, calibration de la commande oculaire, statistiques et réglages.

Temps nécessaire : environ 20 minutes la première fois.

---

## 1. Comprendre avant de commencer

### Pourquoi changer de version ?

| | Version de développement | Version finale 1.1 |
|---|---|---|
| Fluidité | Plus lente (Android l'exécute en mode « débogage ») | Plus fluide : les blocages les plus longs sont environ 3 fois plus courts |
| Lecture des données depuis le PC | Possible (sauvegarde complète en une commande) | Impossible depuis le PC |
| Installation | Bouton ▶ d'Android Studio | Fichier `app-release.apk` |

### La règle d'or : la signature

Chaque application Android porte une **signature**, une sorte de tampon numérique. Android n'accepte de **mettre à jour** une application que si la nouvelle version porte **exactement la même signature** que celle déjà installée.

- **Même signature** : la mise à jour s'installe par-dessus et **toutes les données restent en place**.
- **Signature différente** : Android **refuse** la mise à jour, avec l'erreur `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Ce refus est **sans danger** : l'ancienne version et ses données ne sont pas touchées.

Le seul vrai danger, c'est de **désinstaller** l'application pour contourner ce refus : **désinstaller efface toutes les données de Nina sur la tablette.**

La version 1.1 est signée avec **la même clé** que la version de développement : la clé de débogage d'Android Studio, rangée dans le fichier `C:\Users\<votre nom>\.android\debug.keystore`. Elle s'installe donc par-dessus sans rien effacer, **à condition que la version de développement de la tablette ait été installée depuis ce même PC**.

---

## 2. Les risques, et comment les éviter

| Risque | Conséquence | Comment l'éviter |
|---|---|---|
| Désinstaller Nina | **Toutes les données effacées** | Ne jamais désinstaller. Toujours mettre à jour « par-dessus » (`adb install -r`). |
| Version de développement installée depuis un **autre PC** | La mise à jour est refusée (sans perte) | Faire la mise à jour depuis le PC d'origine, ou copier son fichier `debug.keystore` (voir section 7). |
| Perte du fichier `debug.keystore` (PC changé, réinstallé…) | Plus aucune mise à jour possible sans désinstaller | Sauvegarder ce fichier dès maintenant (section 3.4). |
| Oubli de la sauvegarde complète | Si un problème survient, la calibration, les statistiques et les réglages seraient perdus | Faire la sauvegarde complète **avant** l'installation (section 4). Après, elle n'est plus possible depuis le PC. |
| Installer une version plus ancienne par-dessus une plus récente | Refusée par Android (`INSTALL_FAILED_VERSION_DOWNGRADE`) | Ne pas forcer. La 1.1 porte le numéro de version 2, la version d'origine le numéro 1. |
| Batterie qui se vide pendant l'installation | Installation interrompue (rare, sans perte en général) | Tablette chargée à plus de 30 %, ou branchée. |

---

## 3. Prérequis

### 3.1 Sur le PC
- Le projet Nina, à jour (version 1.1 : `versionName = "1.1"` dans `app/build.gradle.kts`).
- Android Studio, avec le SDK Android : l'outil `adb` se trouve dans `%LOCALAPPDATA%\Android\Sdk\platform-tools\`.
- Le fichier de clé `C:\Users\<votre nom>\.android\debug.keystore`. C'est **le même PC** que celui qui a installé la version de développement sur la tablette.

### 3.2 Sur la tablette
- Le **débogage USB** activé : Paramètres › Options pour les développeurs › Débogage USB.
- La tablette **déverrouillée**, branchée au PC en USB (ou connectée en débogage Wi-Fi).
- Nina **fermée** ou à l'écran d'accueil, avec le mode adulte verrouillé.
- Batterie à plus de 30 %.

### 3.3 Vérifier que le PC voit la tablette
Dans le **Terminal** d'Android Studio (en bas de la fenêtre) :

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

La tablette doit apparaître avec la mention `device`. Si elle affiche `unauthorized`, acceptez le message « Autoriser le débogage USB ? » sur la tablette.

> Pour simplifier la suite, vous pouvez créer un raccourci dans le terminal :
> ```powershell
> Set-Alias adb "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
> ```
> Si plusieurs appareils sont branchés (émulateur + tablette), ajoutez `-s <numéro>` après `adb`, avec le numéro affiché par `adb devices`.

### 3.4 Sauvegarder la clé (une fois pour toutes)
Copiez le fichier `C:\Users\<votre nom>\.android\debug.keystore` sur une clé USB **et** dans votre cloud (Drive, OneDrive…). Sans ce fichier, aucune future mise à jour ne pourra s'installer sans tout effacer.

---

## 4. Étape 1 : sauvegarder les données (obligatoire)

Faites **les deux** sauvegardes : elles se complètent.

### 4.1 Sauvegarde depuis Nina : grilles et photos
Sur la tablette :
1. Maintenez le **cadenas** 2 secondes, puis résolvez le calcul.
2. **⋮ › Partager / Sauvegarder**.
3. Envoyez le fichier `sauvegarde_nina.json` vers un endroit sûr (mail à vous-même, Drive…).

Ce fichier contient la grille, les dossiers, les phrases rapides **et les photos**. Il **ne contient pas** la calibration du regard, les statistiques ni les réglages : c'est pour ça qu'il faut aussi la sauvegarde complète.

### 4.2 Sauvegarde complète depuis le PC : tout le reste
Cette sauvegarde n'est possible **que tant que la version de développement est installée**. Faites-la maintenant.

Dans le terminal d'Android Studio, à la racine du projet :

```powershell
mkdir sauvegardes_tablette -Force
cmd /c "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe exec-out run-as com.example.nina tar cf - files shared_prefs databases no_backup > sauvegardes_tablette\donnees_nina_avant_1.1.tar"
```

> ⚠️ Gardez bien `cmd /c "…"` : dans PowerShell, le `>` seul abîme les fichiers binaires.

**Vérifiez** que la sauvegarde n'est pas vide :

```powershell
Get-Item sauvegardes_tablette\donnees_nina_avant_1.1.tar | Select-Object Length
tar tf sauvegardes_tablette\donnees_nina_avant_1.1.tar | Measure-Object -Line
```

La taille doit faire plusieurs mégaoctets (environ 9 Mo avec les pictos actuels), avec plusieurs centaines de fichiers. Si la commande affiche `run-as: package not debuggable`, c'est que la tablette a **déjà** une version finale : passez directement à l'étape 3.

Le dossier `sauvegardes_tablette` est exclu de git : il reste uniquement sur le PC. Copiez-le aussi sur une clé USB.

---

## 5. Étape 2 : fabriquer la version 1.1

Inutile de passer par le menu **Build › Generate Signed App Bundle / APK** d'Android Studio : la signature est déjà configurée dans `app/build.gradle.kts`. Utilisez simplement le terminal :

```powershell
.\gradlew.bat :app:assembleRelease
```

À la fin, le fichier est ici :

```
app\build\outputs\apk\release\app-release.apk
```

### Vérifier la signature (recommandé)
Comparez la signature de la nouvelle version avec celle installée sur la tablette :

```powershell
$apksigner = (Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" | Sort-Object Name | Select-Object -Last 1).FullName + "\apksigner.bat"

# Nouvelle version
& $apksigner verify --print-certs app\build\outputs\apk\release\app-release.apk | Select-String "SHA-256"

# Version installée sur la tablette
$chemin = (adb shell pm path com.example.nina).Replace("package:", "").Trim()
adb pull $chemin installee.apk
& $apksigner verify --print-certs installee.apk | Select-String "SHA-256"
Remove-Item installee.apk
```

Les deux lignes `SHA-256` doivent être **identiques**. Si elles diffèrent, **arrêtez-vous** et lisez la section 7.

---

## 6. Étape 3 : installer par-dessus

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

- `-r` signifie « remplacer en gardant les données ». **Ne jamais utiliser `adb uninstall`.**
- Le résultat attendu est `Success`.

| Message | Signification | Que faire |
|---|---|---|
| `Success` | Mise à jour installée, données conservées | Passer à la vérification. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Signature différente. **Rien n'a été modifié.** | Section 7. Surtout ne pas désinstaller. |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | La tablette a déjà une version plus récente | Ne rien forcer, vérifier le numéro de version. |
| `more than one device/emulator` | Plusieurs appareils branchés | Ajouter `-s <numéro>` (voir `adb devices`). |

### Vérifier que tout est là
Ouvrez Nina sur la tablette et contrôlez :

- [ ] **Aide › À propos** affiche **Version 1.1**.
- [ ] La grille d'accueil, les dossiers (et leurs sous-dossiers) et les phrases rapides sont identiques.
- [ ] Les **photos** personnelles s'affichent.
- [ ] **⋮ › Commande oculaire** : la calibration est toujours là (nombre de points et précision affichés).
- [ ] **Statistiques** : l'historique est conservé.
- [ ] **Paramètres** : couleur de fond, voix, anti-tremblement… inchangés.
- [ ] Un mot touché est bien prononcé (si le son est coupé, l'alerte rouge « Son coupé » s'affiche).

### Bon à savoir après l'installation
- Les premières minutes, l'app peut être un peu moins fluide. Android l'optimise tout seul quand la tablette est au repos. Pour le faire tout de suite :
  ```powershell
  adb shell cmd package compile -m speed-profile -f com.example.nina
  ```
- Le bouton ▶ d'Android Studio réinstalle une **version de développement**. Elle a la même clé, donc **aucune perte**, mais elle est plus lente. Pour revenir à la version rapide, refaites l'étape 3.

---

## 7. En cas de problème

### La mise à jour est refusée (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`)
La version installée a été signée par **un autre PC**. Les données sont intactes. Trois solutions, de la plus simple à la plus longue :

1. **Faire la mise à jour depuis le PC d'origine** (celui qui avait installé la version de développement).
2. **Utiliser la clé du PC d'origine** : copiez son fichier `C:\Users\<nom>\.android\debug.keystore` à la même place sur ce PC (gardez une copie de l'ancien), puis refaites les étapes 2 et 3.
3. **Dernier recours, avec perte temporaire** : seulement si les deux sauvegardes de l'étape 1 sont faites et vérifiées.
   1. `adb uninstall com.example.nina` (efface tout sur la tablette).
   2. Installez la **version de développement** depuis ce PC (bouton ▶ d'Android Studio).
   3. Restaurez la sauvegarde complète (section suivante).
   4. Refaites l'étape 3.

### Restaurer la sauvegarde complète
La restauration n'est possible que dans une **version de développement**. Si besoin, installez-en une avec le bouton ▶ d'Android Studio : même clé, donc sans perte.

```powershell
adb shell am force-stop com.example.nina
cmd /c "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe exec-in run-as com.example.nina tar xf - < sauvegardes_tablette\donnees_nina_avant_1.1.tar"
adb shell am start -n com.example.nina/.MainActivity
```

Puis vérifiez la liste de contrôle de l'étape 3.

### Seulement les grilles et les photos à récupérer
Sur la tablette : **⋮ › Restaurer**, puis choisissez le fichier `sauvegarde_nina.json` de l'étape 1.1.

### La grille semble avoir disparu au démarrage
Nina affiche alors « Grille récupérée » : elle a repris la **sauvegarde automatique** du jour, enregistrée sur la tablette. Les sauvegardes automatiques des 7 derniers jours sont aussi dans **⋮ › Sauvegardes automatiques…**.

---

## 8. Résumé express

1. Sauvegarder la clé `debug.keystore` (une fois pour toutes).
2. Nina › ⋮ › **Partager / Sauvegarder**.
3. Sauvegarde complète depuis le PC (`exec-out run-as … tar`).
4. `.\gradlew.bat :app:assembleRelease`
5. Vérifier que les signatures sont identiques.
6. `adb install -r app\build\outputs\apk\release\app-release.apk` (**jamais** `uninstall`).
7. Vérifier : À propos = 1.1, grilles, photos, calibration, statistiques, réglages.
