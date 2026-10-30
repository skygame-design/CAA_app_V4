package com.example.nina

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

/* Aide & Tutoriel : rubriques, contenu et mise en page. */

/** Blocs de contenu d'une rubrique d'aide. */
private sealed interface HelpBlock
private data class HelpText(val text: String) : HelpBlock
private data class HelpSteps(val title: String?, val steps: List<String>) : HelpBlock
/** Tableau à deux colonnes : exemple -> résultat, ou option -> explication. */
private data class HelpRows(val title: String?, val rows: List<Pair<String, String>>, val arrow: Boolean = false) : HelpBlock
private data class HelpTip(val text: String, val isNew: Boolean = false) : HelpBlock
private data class HelpColors(val colors: List<Pair<Long, String>>) : HelpBlock
private data object HelpScreenMap : HelpBlock
/** Nom, version (lue dans l'application installée) et copyright. */
private data object HelpAbout : HelpBlock
private data class HelpTopic(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val isNew: Boolean = false,
    val blocks: List<HelpBlock>
)
private val helpTopics = listOf(
    HelpTopic(
        Icons.Default.Home, "Bien démarrer", "Les zones de l'écran",
        blocks = listOf(
            HelpText("Nina permet de parler en touchant des images (pictogrammes). Chaque image touchée est dite à voix haute."),
            HelpScreenMap,
            HelpSteps(
                "Les zones de l'écran", listOf(
                    "Barre de phrase (en haut) : les mots choisis s'y alignent. Le bouton vert ▶, ou un appui sur la barre, lit la phrase.",
                    "Dossiers (colonne de gauche) : 🏠 Accueil toujours en haut (il dit « Accueil » et revient à la première page), puis les catégories (Actions, Nourriture…). Un toucher ouvre le dossier depuis n'importe où ; le dossier ouvert est encadré. S'il y a trop de dossiers, ▲ ▼ changent de page.",
                    "Grille (au centre) : à l'accueil, les mots essentiels (moi, vouloir, encore, pas…) toujours à la même place ; dans un dossier, ses mots. Elle ne défile jamais : s'il y a trop de pictos, ◀ ▶ en bas changent de page et disent « Page suivante » ou « Page précédente ». Le bouton ← revient en arrière dans les sous-dossiers et dit « Retour ».",
                    "Phrases rapides (colonne de droite, éclair ⚡) : oui, non, bonjour, merci, toilettes… dites tout de suite, sans passer par la phrase.",
                    "Cadenas (petit, gris, en haut à droite) : à maintenir 2 secondes pour ouvrir le mode adulte."
                )
            ),
            HelpTip("À droite de la barre de phrase, la loupe 🔍 cherche un mot et peut montrer où il se trouve ; le clavier ⌨ permet d'écrire un mot qui n'a pas de picto."),
            HelpTip(
                "Bien organiser : un mot = une seule place (l'enfant mémorise le geste). Gardez 6 à 8 grands dossiers et rangez le reste en sous-dossiers. " +
                "Mode adulte › ⋮ › « Grilles prêtes » installe une grille complète (3 niveaux, de 12 à 30 mots à l'accueil), à adapter ensuite avec l'orthophoniste.",
                isNew = true
            )
        )
    ),
    HelpTopic(
        Icons.Default.GridView, "Grilles prêtes", "Installer un vocabulaire complet", isNew = true,
        blocks = listOf(
            HelpText("Plutôt que de créer chaque picto à la main, installez une grille déjà remplie : mots essentiels à l'accueil, dossiers thématiques à gauche et phrases rapides à droite. Elle sert de point de départ, que vous adaptez ensuite."),
            HelpSteps(
                "Installer une grille", listOf(
                    "Ouvrez le mode adulte (maintenir le cadenas 2 secondes, puis le calcul).",
                    "Dans la barre jaune, touchez ⋮ puis « Grilles prêtes… ».",
                    "Choisissez un niveau à gauche : l'aperçu à droite montre l'accueil, chaque dossier et les phrases rapides.",
                    "Touchez « Installer ». La grille, les dossiers, les phrases rapides et la taille de grille sont réglés d'un coup.",
                    "Les images se téléchargent dans la tablette (il faut du réseau une fois), puis tout fonctionne hors ligne."
                )
            ),
            HelpRows(
                "Les trois niveaux", listOf(
                    "Premiers mots" to "débutant — 51 mots, 12 à l'accueil, 5 petits dossiers, 4 pictos par rangée",
                    "Vocabulaire courant" to "intermédiaire — 95 mots, 20 à l'accueil, 6 dossiers dont Jeux, 5 par rangée ; ajoute J'ai mal et Stop",
                    "Vocabulaire étendu" to "avancé — 149 mots, 30 à l'accueil avec les questions (quoi, où, qui, quand, pourquoi), sous-dossiers, 6 par rangée"
                )
            ),
            HelpText("À l'accueil se trouvent les mots les plus employés par les enfants (moi, vouloir, encore, fini, pas…), rangés par couleur dans l'ordre de la phrase. D'un niveau à l'autre, chaque mot garde exactement sa place : les nouveaux mots s'ajoutent à droite et en bas. L'enfant peut donc passer au niveau suivant sans réapprendre ses gestes."),
            HelpTip("Installer une grille remplace tous les pictos actuels. Votre disposition est gardée : ⋮ › « Revenir à la disposition précédente » la remet en place. Pour garder une copie durable, faites aussi ⋮ › « Partager / Sauvegarder » avant."),
            HelpTip("Ces listes sont un point de départ, pas un vocabulaire validé pour un enfant précis. Montrez-les à l'orthophoniste, puis adaptez-les : glisser-déposer, mode Édition, ajout de photos de la famille et des objets de la maison.")
        )
    ),
    HelpTopic(
        Icons.Default.RecordVoiceOver, "Construire une phrase", "Barre de phrase et grammaire",
        blocks = listOf(
            HelpSteps(
                null, listOf(
                    "Touche les pictos dans l'ordre : chaque mot est dit et s'ajoute à la barre, avec son image et son texte.",
                    "Pour corriger, ⌫ efface le dernier mot. La poubelle rouge efface toute la phrase.",
                    "Appuie sur ▶ ou touche la barre : Nina lit la phrase en la mettant en bon français."
                )
            ),
            HelpRows(
                "Ce que Nina corrige", listOf(
                    "Moi vouloir manger" to "Je veux manger",
                    "Maman aller maison" to "Maman va maison",
                    "Nous manger gâteau" to "Nous mangeons gâteau",
                    "Moi pas aimer" to "Je n'aime pas",
                    "Moi se laver" to "Je me lave",
                    "Moi boire de le eau" to "Je bois de l'eau",
                    "Jouer avec moi" to "Jouer avec moi (inchangé)"
                ), arrow = true
            ),
            HelpText("Seul le premier verbe après le sujet est conjugué ; les suivants restent à l'infinitif (« je veux manger »). Nina n'ajoute pas les petits mots (le, à la…) : il faut les choisir."),
            HelpTip("Après un mot choisi dans un dossier, la grille revient seule à l'accueil. Réglable dans Paramètres › Tactile.", isNew = true)
        )
    ),
    HelpTopic(
        Icons.Default.TextFields, "Formes du mot", "Conjuguer, mettre au pluriel", isNew = true,
        blocks = listOf(
            HelpText("Cadenas fermé, un appui long sur un picto ouvre ses différentes formes. Toucher une forme l'ajoute à la phrase et la fait dire."),
            HelpRows(
                "Exemples", listOf(
                    "Manger" to "manger, mange, manges, mangeons, mangez, mangent, mangé",
                    "Aller" to "aller, vais, vas, va, allons, allez, vont, allé",
                    "Chat" to "chat, chats",
                    "Cheval" to "cheval, chevaux"
                ), arrow = true
            ),
            HelpText("Sous chaque forme, une indication dit pour qui elle sert (je, tu, nous… ou « passé »). Pratique pour apprendre à conjuguer : « nous » puis « mangeons »."),
            HelpTip("Rien ne s'ouvre pour les dossiers, les phrases rapides et les petits mots (encore, oui, avec…). Cadenas ouvert, l'appui long sert à déplacer les pictos.")
        )
    ),
    HelpTopic(
        Icons.Default.TouchApp, "Montrer un mot", "Modélisation pas à pas", isNew = true,
        blocks = listOf(
            HelpText("Pour apprendre où se trouve un mot, Nina montre le chemin case par case, comme un adulte qui pointe du doigt."),
            HelpSteps(
                null, listOf(
                    "Touche la loupe 🔍 et tape le début du mot.",
                    "Touche le résultat ou le bouton « Montrer ».",
                    "La case à toucher clignote en orange. Un bandeau indique l'étape (1/2, 2/2…).",
                    "À chaque bonne case touchée, la suivante s'allume. Le guidage s'arrête sur le mot."
                )
            ),
            HelpText("Conseil : montrez le chemin vous-même pendant que l'enfant regarde, puis laissez-le refaire. C'est la « modélisation », recommandée en CAA."),
            HelpTip("« Arrêter » dans le bandeau annule le guidage. Les pictos masqués ne sont pas proposés.")
        )
    ),
    HelpTopic(
        Icons.Default.Palette, "Code couleur", "Repérer le type de mot",
        blocks = listOf(
            HelpText("Les couleurs aident à repérer le type de mot (code Fitzgerald). La couleur se choisit dans la fenêtre « Modifier le picto »."),
            HelpColors(
                listOf(
                    0xFFFFD54FL to "Personnes : sujets, pronoms (moi, maman)",
                    0xFF81C784L to "Actions : verbes (vouloir, jouer)",
                    0xFFFFB74DL to "Choses : noms, objets, aliments",
                    0xFF64B5F6L to "Descriptifs : adjectifs, émotions",
                    0xFFF06292L to "Social : politesse, salutations",
                    0xFFBA68C8L to "Questions : mots interrogatifs",
                    0xFFE0E0E0L to "Petits mots : prépositions, liens"
                )
            )
        )
    ),
    HelpTopic(
        Icons.Default.Lock, "Mode adulte", "Cadenas, barre d'outils, déplacer",
        blocks = listOf(
            HelpSteps(
                "Ouvrir le mode adulte", listOf(
                    "Maintiens le petit cadenas gris (en haut à droite) pendant 2 secondes : un anneau se remplit. Un simple appui ne fait rien, pour que l'enfant ne l'ouvre pas par hasard.",
                    "Résous le petit calcul (par exemple 3 + 4).",
                    "Une barre jaune « Mode adulte » apparaît en haut de l'écran, avec tous les outils."
                )
            ),
            HelpRows(
                "La barre du mode adulte", listOf(
                    "Ajouter" to "créer un picto : dans la grille, la colonne des dossiers ou les phrases rapides",
                    "Édition" to "modifier les pictos (un crayon apparaît) ; le bouton reste foncé tant que l'édition est active",
                    "Paramètres" to "couleurs, voix, réglages tactiles",
                    "Statistiques" to "suivi de l'utilisation",
                    "Aide" to "cette aide",
                    "− 4 par rangée +" to "taille de la grille, de 1 à 8 pictos par rangée",
                    "⋮" to "calibrer le tactile, mode Kiosque, partager / restaurer la sauvegarde, grilles prêtes",
                    "Verrouiller" to "referme le mode adulte (l'édition s'arrête aussi)"
                )
            ),
            HelpSteps(
                "Déplacer un picto (glisser-déposer)", listOf(
                    "Cadenas ouvert, fais un appui long sur un picto puis glisse-le.",
                    "Lâché au centre d'une case : les deux pictos s'échangent.",
                    "Lâché sur le bord d'une case : il s'insère avant ou après (bord gauche ou droit dans la grille, haut ou bas dans les colonnes). Un trait bleu montre la place.",
                    "Ça marche aussi à l'intérieur de la colonne des dossiers et de celle des phrases rapides.",
                    "D'une zone à une autre (grille, dossiers, phrases rapides) : le picto y est copié, à l'endroit visé ; l'original reste à sa place.",
                    "Pour l'emmener sur une autre page, garde-le une seconde sur le bouton ◀ ▶ (grille) ou ▲ ▼ (colonnes) : la page tourne."
                )
            ),
            HelpTip("Referme le cadenas avant de rendre la tablette : le mode Édition se coupe automatiquement.")
        )
    ),
    HelpTopic(
        Icons.Default.Edit, "Personnaliser un picto", "Ajouter, modifier, voix, image",
        blocks = listOf(
            HelpSteps(
                "Ajouter un picto", listOf(
                    "Barre du mode adulte › Ajouter, puis choisis la zone : grille (ou dossier ouvert), colonne des dossiers ou phrases rapides.",
                    "Choisis un texte, une image (recherche dans les banques ARASAAC, Mulberry, Sclera… ou photo de la galerie) et une couleur.",
                    "Touche « Créer »."
                )
            ),
            HelpSteps(
                "Modifier un picto", listOf(
                    "Barre du mode adulte › Édition : un crayon apparaît sur chaque picto.",
                    "Touche le picto à modifier.",
                    "Onglets : Apparence, Audio, Dossier, Suppression.",
                    "Touche « Enregistrer les modifications ». Touche à nouveau « Édition » pour finir."
                )
            ),
            HelpRows(
                "Les options", listOf(
                    "Texte du picto" to "ce qui s'affiche et s'ajoute à la phrase",
                    "Texte prononcé" to "ce qui est dit quand on le touche (Toilettes → « J'ai besoin d'aller aux toilettes »). Nouveau",
                    "Audio" to "enregistrer sa propre voix, jouée à la place de la synthèse",
                    "Dossier" to "transformer en dossier, ou déplacer le picto dans un dossier",
                    "Phrase rapide" to "dite tout de suite, sans aller dans la phrase",
                    "Masquer" to "caché pour l'enfant ; sa place reste vide pour ne pas déplacer les autres"
                )
            )
        )
    ),
    HelpTopic(
        Icons.Default.BarChart, "Statistiques", "Suivi et export CSV", isNew = true,
        blocks = listOf(
            HelpText("Chaque mot touché et chaque phrase lue avec ▶ sont notés avec la date et l'heure. Ces données restent sur la tablette."),
            HelpRows(
                null, listOf(
                    "Période" to "aujourd'hui, 7 jours, 30 jours ou tout",
                    "Mots" to "les mots les plus utilisés",
                    "Par jour" to "le nombre de mots chaque jour",
                    "Phrases" to "l'historique daté des phrases prononcées",
                    "Exporter (CSV)" to "un fichier pour Excel, à envoyer par mail ou Drive (par exemple à l'orthophoniste)"
                )
            ),
            HelpTip("« Remettre à zéro » demande une confirmation, puis efface tout l'historique.")
        )
    ),
    HelpTopic(
        Icons.Default.Visibility, "Commande oculaire", "Choisir les pictos du regard", isNew = true,
        blocks = listOf(
            HelpText("La caméra frontale suit le regard : l'enfant choisit un picto en le fixant, sans toucher l'écran. Tout se passe dans la tablette, aucune image n'est enregistrée."),
            HelpSteps(
                "Mise en place", listOf(
                    "Posez la tablette sur un support stable, face à l'enfant, à 40–60 cm, visage bien éclairé de face.",
                    "Réglez la taille de grille que l'enfant utilisera (3 ou 4 pictos par rangée conseillés).",
                    "Mode adulte › ⋮ › Commande oculaire. Autorisez la caméra la première fois.",
                    "« Calibrer sur les pictos » : d'abord, les yeux restent sur l'étoile au centre pendant que la tête tourne doucement à gauche, à droite, en haut, en bas (quelques secondes). Puis l'enfant suit des yeux l'étoile, qui se pose sur chaque picto.",
                    "« Essai sur les pictos » : vérifiez qu'en fixant une case, l'anneau se remplit sur la bonne.",
                    "Activez « Sélection par le regard », puis refermez le cadenas."
                )
            ),
            HelpRows(
                "Ce qui rend la sélection fiable", listOf(
                    "Calibration sur les pictos" to "les mesures sont prises à l'emplacement exact de chaque picto, et l'erreur restante y est corrigée",
                    "Aimant" to "le regard est rattaché à la case la plus proche, et n'en change qu'après une hésitation franche",
                    "Fixation" to "l'anneau orange se remplit (1,2 s par défaut, réglable) : un coup d'œil ou un clignement ne déclenche rien",
                    "Repos" to "après une sélection, la même case ne se redéclenche qu'après un court repos",
                    "Rotation de la tête" to "mesurée au début de la calibration, puis compensée : tourner un peu la tête ne fait plus dériver le pointeur",
                    "Calibration continue" to "chaque picto touché, ou choisi du regard sans être effacé aussitôt, devient un point de calibration : la calibration s'adapte à l'enfant au fil de l'utilisation, zone par zone",
                    "Petits boutons" to "dossiers, phrases rapides, Lire, ⌫ attirent le regard dans un rayon d'au moins 1,3 cm",
                    "Anneau tolérant" to "un tremblement ou un passage entre deux cases met l'anneau en pause au lieu de le vider",
                    "Œil trop fermé" to "quand la paupière cache l'iris (regard très bas, fatigue), le pointeur reste immobile au lieu de sauter",
                    "Lissage automatique" to "le pointeur est lissé davantage quand il tremble, et reste réactif quand il est stable",
                    "Position et lumière" to "la fenêtre Commande oculaire signale une tête trop penchée ou tournée, une position différente de celle de la calibration, et une lumière trop faible",
                    "Recentrage automatique" to "si le pointeur tombe toujours un peu trop haut ou trop bas, les pictos validés le recentrent peu à peu ; « Recentrer » remet ce réglage à zéro"
                )
            ),
            HelpRows(
                "Si le pointeur manque de précision", listOf(
                    "Grille adaptée au regard" to "moins de pictos par rangée (4 conseillés), des dossiers et phrases rapides plus hauts, et de grands espaces entre les cases, quand la sélection par le regard est active",
                    "Sélection en deux temps" to "fixer une zone l'agrandit deux fois (anneau bleu), puis fixer le picto agrandi l'active (anneau orange). Sans choix pendant 4 s, l'écran revient à la normale"
                )
            ),
            HelpRows(
                "Faire évoluer la calibration", listOf(
                    "Retouche rapide" to "6 pictos remesurés et ajoutés à la calibration existante : après un changement de lumière, de position ou de support",
                    "Calibrer sur les pictos" to "tout refaire, par exemple après un changement de taille de grille",
                    "Vérifier la précision" to "mesure l'écart sur 5 pictos, en cm"
                )
            ),
            HelpTip("Interrupteur général en haut de la fenêtre Commande oculaire : désactivée, la caméra est éteinte et rien ne réagit au regard ; réglages et calibration sont gardés pour la prochaine fois."),
            HelpTip("La sélection par le regard est coupée en mode adulte et quand une fenêtre est ouverte. Les phrases rapides, Accueil, Play, ⌫, ← et les boutons de page se choisissent aussi du regard.")
        )
    ),
    HelpTopic(
        Icons.Default.PhotoCamera, "Photos et planche imprimable", "Vraies photos, version papier", isNew = true,
        blocks = listOf(
            HelpText("Pour un enfant qui débute, une vraie photo (sa tasse, son doudou, maman) parle souvent plus qu'un pictogramme."),
            HelpSteps(
                "Faire un picto avec une photo", listOf(
                    "Mode adulte › Ajouter (ou Édition, puis toucher un picto).",
                    "« Prendre une photo » : l'appareil photo s'ouvre ; cadrez l'objet ou la personne de près, sur un fond simple.",
                    "Ou « Galerie » pour une photo déjà prise.",
                    "La photo est réduite, redressée et enregistrée dans la tablette : elle reste visible sans internet."
                )
            ),
            HelpTip("Le fichier « Partager / Sauvegarder » emporte aussi les photos : une restauration sur une autre tablette les retrouve."),
            HelpSteps(
                "Imprimer la planche", listOf(
                    "Mode adulte › ⋮ › Planche imprimable.",
                    "Gardez le même nombre de pictos par rangée qu'à l'écran : les mots restent aux mêmes places.",
                    "« Imprimer » ouvre l'impression d'Android (imprimante, ou Enregistrer au format PDF) ; « Partager le PDF » l'envoie par mail, Drive…"
                )
            ),
            HelpTip("Page d'accueil comme l'écran (dossiers à gauche, phrases rapides à droite), puis une page par dossier. Plastifiez-la : c'est le secours quand la tablette n'est pas là.")
        )
    ),
    HelpTopic(
        Icons.Default.Accessibility, "Réglages et accessibilité", "Vue, voix, toucher",
        blocks = listOf(
            HelpRows(
                "Dans Paramètres", listOf(
                    "Son coupé" to "si le volume multimédia de la tablette est à zéro, une alerte rouge « Son coupé » apparaît dans la barre de phrase ; la toucher ouvre le réglage du volume",
                    "Veille" to "tant que Nina est ouverte, l'écran ne se met pas en veille et ne se verrouille pas",
                    "Contraste élevé (CVI)" to "fond noir et bordures jaunes, pour la déficience visuelle",
                    "Couleur de fond" to "couleurs proposées ou personnalisées",
                    "Voix" to "hauteur (voix d'enfant) et vitesse",
                    "Anti-tremblement" to "ignore les appuis répétés trop rapprochés",
                    "Retour à l'accueil" to "revenir à l'accueil après un mot pris dans un dossier. Nouveau"
                )
            ),
            HelpRows(
                "Dans la barre du mode adulte", listOf(
                    "Taille de grille" to "boutons − et + de la barre du mode adulte : de 1 à 8 pictos par rangée. La grille remplit l'écran sans défiler ; s'il y a trop de pictos, des pages ◀ ▶ apparaissent. Nouveau",
                    "⋮ › Calibrer le tactile" to "attraper 5 fois la coccinelle : l'écran se décale pour compenser",
                    "⋮ › Mode Kiosque" to "empêche de quitter Nina par erreur"
                )
            ),
            HelpTip("Une fois la grille installée, évitez de changer le nombre de colonnes : l'enfant retient l'emplacement des mots."),
            HelpTip(
                "Voix sans internet : si la voix française de la tablette a besoin d'internet, Nina serait muette sans Wi-Fi. " +
                "Un point orange apparaît alors sur le cadenas, et un bouton orange dans la barre du mode adulte explique comment installer la voix (une seule fois).",
                isNew = true
            ),
            HelpTip(
                "Plein écran : Nina cache l'heure, la batterie et les boutons Android (‹ ○ |||) pour laisser toute la place aux pictos et éviter les appuis par erreur. " +
                "Pour les faire réapparaître quelques secondes, glissez le doigt depuis le bord haut ou bas de l'écran ; ils se recachent seuls.",
                isNew = true
            )
        )
    ),
    HelpTopic(
        Icons.Default.Save, "Sauvegarde", "Changer de tablette",
        blocks = listOf(
            HelpRows(
                null, listOf(
                    "⋮ › Partager / Sauvegarder" to "crée un fichier avec la grille et les deux colonnes, à envoyer (Drive, mail…)",
                    "⋮ › Restaurer" to "recharge un fichier de sauvegarde, y compris les anciennes",
                    "⋮ › Sauvegardes automatiques" to "revenir à la grille d'un des 7 derniers jours, en cas de mauvaise manipulation. Nouveau"
                )
            ),
            HelpTip(
                "Sauvegarde automatique : chaque jour d'utilisation, Nina garde une copie de la grille dans la tablette (7 jours). " +
                "Si la grille enregistrée est abîmée, Nina la récupère toute seule au démarrage et vous prévient. " +
                "Ces copies restent dans la tablette : pour changer de tablette, utilisez toujours « Partager / Sauvegarder ».",
                isNew = true
            ),
            HelpTip("Les images des banques (ARASAAC, Mulberry…) sont enregistrées dans la tablette et fonctionnent hors réseau. Sur une nouvelle tablette, elles sont retéléchargées automatiquement après la restauration (il faut du réseau une fois)."),
            HelpTip("Les photos de la galerie et les voix enregistrées restent sur la tablette : sur une nouvelle tablette, elles seront à refaire. Les statistiques ne sont pas dans la sauvegarde.")
        )
    ),
    HelpTopic(
        Icons.Default.Image, "Images et crédits", "Banques de pictogrammes", isNew = true,
        blocks = listOf(
            HelpText("En créant ou modifiant un picto, « Chercher » interroge plusieurs banques à la fois. Les filtres permettent de n'en voir qu'une. L'image choisie est enregistrée dans la tablette : elle reste visible sans réseau."),
            HelpRows(
                "Les banques et leurs licences", listOf(
                    "ARASAAC" to "Sergio Palao, Gouvernement d'Aragon (arasaac.org) — CC BY-NC-SA",
                    "Mulberry" to "Steve Lee, mulberrysymbols.org — CC BY-SA",
                    "Sclera" to "Sclera vzw, sclera.be — CC BY-NC",
                    "Blissymbolics" to "Blissymbolics Communication International — CC BY-SA",
                    "Tawasol, Twemoji, Noun Project…" to "via opensymbols.org — licence indiquée sous l'image choisie"
                )
            ),
            HelpText("Recherche fournie par globalsymbols.com et opensymbols.org. « NC » signifie usage non commercial : parfait pour un usage personnel ou thérapeutique, mais pas pour vendre l'application."),
            HelpTip("Si une banque ne répond pas, les autres s'affichent quand même. Sans réseau, la recherche est impossible, mais tous les pictos déjà créés restent visibles.")
        )
    ),
    HelpTopic(
        Icons.Default.Info, "À propos", "Version et copyright",
        blocks = listOf(HelpAbout)
    )
)
private val HelpAccent = Color(0xFF4A5D91)
private val HelpNew = Color(0xFFFF6D00)
/**
 * Aide & Tutoriel en plein écran : rubriques à gauche, contenu à droite,
 * boutons Précédent / Suivant pour la lire comme un tutoriel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    val topic = helpTopics[selected]

    // decorFitsSystemWindows = false : la fenêtre couvre tout l'écran (sinon une bande de l'appli reste visible en bas)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        HideSystemBarsInDialog()
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF0F0F5)) {
            Column(modifier = Modifier.systemBarsPadding()) {
                TopAppBar(
                    title = { Text("Aide & Tutoriel", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Fermer") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                Row(modifier = Modifier.fillMaxSize()) {
                    // Sommaire
                    LazyColumn(
                        modifier = Modifier.width(300.dp).fillMaxHeight().background(Color.White),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(helpTopics.size) { index ->
                            HelpNavItem(helpTopics[index], selected = index == selected) { selected = index }
                        }
                    }

                    // Contenu de la rubrique (défilement remis en haut à chaque changement)
                    key(selected) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(topic.icon, contentDescription = null, tint = HelpAccent, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(topic.title, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color(0xFF1F2A4D))
                                        if (topic.isNew) { Spacer(modifier = Modifier.width(12.dp)); NewBadge() }
                                    }
                                    Text(topic.subtitle, fontSize = 16.sp, color = Color.Gray)
                                }
                                Text("${selected + 1} / ${helpTopics.size}", color = Color.Gray)
                            }

                            topic.blocks.forEach { HelpBlockView(it) }

                            // Navigation façon tutoriel
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                if (selected > 0) {
                                    OutlinedButton(onClick = { selected-- }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(helpTopics[selected - 1].title)
                                    }
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                if (selected < helpTopics.lastIndex) {
                                    Button(onClick = { selected++ }, colors = ButtonDefaults.buttonColors(containerColor = HelpAccent)) {
                                        Text(helpTopics[selected + 1].title)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                } else {
                                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = HelpAccent)) { Text("Terminer") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun HelpNavItem(topic: HelpTopic, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFE3E7F5) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(topic.icon, contentDescription = null, tint = if (selected) HelpAccent else Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(topic.title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) HelpAccent else Color.DarkGray)
            Text(topic.subtitle, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
        }
        if (topic.isNew) Box(modifier = Modifier.size(8.dp).background(HelpNew, CircleShape))
    }
}
@Composable
private fun NewBadge() {
    Text(
        "NOUVEAU", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White,
        modifier = Modifier.background(HelpNew, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
@Composable
private fun HelpBlockView(block: HelpBlock) {
    when (block) {
        is HelpText -> Text(block.text, fontSize = 17.sp, lineHeight = 26.sp, color = Color(0xFF333333))

        is HelpAbout -> {
            val context = LocalContext.current
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Nina", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = HelpAccent)
                    Text("Communication alternative et améliorée (CAA)", fontSize = 17.sp, color = Color(0xFF333333))
                    Text("Version $version", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                    Spacer(Modifier.height(8.dp))
                    Text("© 2026 Skygame", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }

        is HelpSteps -> HelpCard(block.title) {
            block.steps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp).background(HelpAccent, CircleShape)) {
                        Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(step, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        is HelpRows -> HelpCard(block.title) {
            block.rows.forEachIndexed { i, (left, right) ->
                if (i > 0) HorizontalDivider(color = Color(0xFFE8E8EE))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(left, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(230.dp))
                    if (block.arrow) Text("→", color = HelpAccent, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    val isNew = right.endsWith(". Nouveau")
                    Text(right.removeSuffix(". Nouveau"), fontSize = 16.sp, color = Color(0xFF444444), modifier = Modifier.weight(1f))
                    if (isNew) { Spacer(modifier = Modifier.width(8.dp)); NewBadge() }
                }
            }
        }

        is HelpTip -> Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
                .background(if (block.isNew) Color(0xFFFFF3E0) else Color(0xFFE8F5E9), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(if (block.isNew) "✨" else "💡", fontSize = 20.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(block.text, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f))
            if (block.isNew) { Spacer(modifier = Modifier.width(8.dp)); NewBadge() }
        }

        is HelpColors -> HelpCard(null) {
            block.colors.forEach { (color, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(modifier = Modifier.size(32.dp).background(Color(color), RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(label, fontSize = 16.sp)
                }
            }
        }

        HelpScreenMap -> ScreenMap()
    }
}
@Composable
internal fun HelpCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            title?.let { Text(it, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = HelpAccent, modifier = Modifier.padding(bottom = 8.dp)) }
            content()
        }
    }
}
/** Plan simplifié de l'écran de Nina, avec le nom de chaque zone. */
@Composable
private fun ScreenMap() {
    @Composable
    fun Zone(label: String, color: Long, modifier: Modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(Color(color), RoundedCornerShape(10.dp)).padding(6.dp)
        ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color(0xFF333333)) }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF3E0)), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(230.dp).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.height(50.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Zone("▶", 0xFFA5D6A7, Modifier.width(50.dp).fillMaxHeight())
                    Zone("Barre de phrase", 0xFFFFFFFF, Modifier.weight(1f).fillMaxHeight())
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Zone("🏠 Accueil\n+ dossiers", 0xFFE0E0E0, Modifier.width(90.dp).fillMaxHeight())
                    Zone("Grille : mots essentiels", 0xFFC8E6C9, Modifier.weight(1f).fillMaxHeight())
                }
            }
            Column(modifier = Modifier.width(110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Zone("🔒 maintenir", 0xFFE0E0E0, Modifier.fillMaxWidth().height(50.dp))
                Zone("⚡ Phrases\nrapides", 0xFFBBDEFB, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}
