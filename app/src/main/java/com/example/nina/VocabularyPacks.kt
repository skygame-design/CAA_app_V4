package com.example.nina

import androidx.compose.runtime.mutableStateListOf

/**
 * Une grille prête à l'emploi : mots essentiels (grille d'accueil), dossiers thématiques (colonne de gauche)
 * et phrases rapides (colonne de droite), avec le nombre de pictos par rangée conseillé.
 */
data class VocabularyPack(
    val id: String,
    val name: String,
    val level: String,
    val description: String,
    val columns: Int,
    val build: () -> NinaBackup
)

/**
 * Grilles prêtes, du plus simple au plus riche, construites sur les mots les plus utilisés à l'oral par les enfants
 * (vocabulaire de base ou « core vocabulary » : pronoms, verbes fréquents, petits mots) et sur des thèmes du quotidien.
 *
 * Organisation commune (planification motrice) : à l'accueil, une colonne par type de mot, dans l'ordre de la phrase
 * (qui → action → descriptif → petits mots). D'un niveau à l'autre, les premiers mots gardent la même position :
 * l'enfant peut passer au niveau suivant sans réapprendre ses gestes.
 */
object VocabularyPacks {

    // Couleurs du code Fitzgerald (mêmes teintes que l'éditeur)
    private const val PERSONNE = 0xFFFFF9C4L
    private const val ACTION = 0xFFC8E6C9L
    private const val CHOSE = 0xFFFFCC80L
    private const val DESCRIPTIF = 0xFFBBDEFBL
    private const val SOCIAL = 0xFFF8BBD0L
    private const val LIEU = 0xFFE1BEE7L
    private const val QUESTION = 0xFFE1BEE7L
    private const val PETIT_MOT = 0xFFEEEEEEL
    private const val NON_ROUGE = 0xFFFFCDD2L

    /**
     * Mots dont la première image ARASAAC ne convient pas (vérifiés à la main) : identifiant ARASAAC à utiliser.
     * Ex. la recherche « pas » donne un pied, « ça » du calcium.
     */
    val PINNED_ARASAAC = mapOf(
        "pas" to 5525,
        "ça" to 7091,
        "tata" to 30271,              // sinon : tatami
        "tonton" to 30255,            // sinon : rien
        "j'ai mal" to 30620,
        "fruits et légumes" to 28339,
        "ressentis" to 32590,         // « sensations et émotions »
        "besoins" to 37160,
    )

    private fun w(label: String, color: Long) =
        AACCell(label, color, PINNED_ARASAAC[label.lowercase()]?.let { PictoSearch.arasaacUrl(it) } ?: "FETCH")

    private fun words(color: Long, vararg labels: String) = labels.map { w(it, color) }

    private fun folder(label: String, color: Long, words: List<AACCell>) =
        AACCell(label, color, PINNED_ARASAAC[label.lowercase()]?.let { PictoSearch.arasaacUrl(it) } ?: "FETCH",
            isFolder = true, content = mutableStateListOf(*words.toTypedArray()))

    private fun quick(label: String, color: Long, spoken: String? = null) =
        AACCell(label, color, PINNED_ARASAAC[label.lowercase()]?.let { PictoSearch.arasaacUrl(it) } ?: "FETCH",
            isQuickPhrase = true, spokenText = spoken)

    /** Lit une grille donnée rangée par rangée (chaque chaîne = une rangée, mots séparés par « | »). */
    private fun grid(vararg rows: Pair<String, List<Long>>): List<AACCell> =
        rows.flatMap { (row, colors) -> row.split("|").map { it.trim() }.mapIndexed { i, label -> w(label, colors[i]) } }

    private val QUICK_BASE get() = listOf(
        quick("Oui", ACTION), quick("Non", NON_ROUGE), quick("Bonjour", PERSONNE), quick("Merci", DESCRIPTIF),
        quick("Toilettes", LIEU, "J'ai besoin d'aller aux toilettes")
    )
    private val QUICK_PLUS get() = QUICK_BASE + listOf(
        quick("J'ai mal", NON_ROUGE, "J'ai mal"), quick("Stop", NON_ROUGE, "Stop, arrête")
    )

    // --- Niveau 1 : premiers mots ---
    private fun premiersMots(): NinaBackup {
        val c4 = listOf(PERSONNE, ACTION, SOCIAL, PETIT_MOT)
        val core = grid(
            "Moi | Vouloir | Encore | Avec" to c4,
            "Toi | Aller | Fini | Pas" to c4,
            "Lui | Aider | Ça | Ici" to listOf(PERSONNE, ACTION, PETIT_MOT, PETIT_MOT)
        )
        val folders = listOf(
            folder("Actions", ACTION, words(ACTION, "Manger", "Boire", "Jouer", "Dormir", "Regarder", "Écouter")),
            folder("Nourriture", CHOSE, words(CHOSE, "Eau", "Lait", "Pain", "Pomme", "Banane", "Gâteau", "Yaourt", "Pâtes")),
            folder("Personnes", PERSONNE, words(PERSONNE, "Maman", "Papa", "Frère", "Sœur", "Mamie", "Papi", "Maîtresse", "Copain")),
            folder("Émotions", DESCRIPTIF, words(DESCRIPTIF, "Content", "Triste", "En colère", "Peur", "Fatigué", "Mal")),
            folder("Lieux", LIEU, words(LIEU, "Maison", "École", "Parc", "Dehors", "Voiture", "Lit")),
        )
        return NinaBackup(core, folders, QUICK_BASE)
    }

    // --- Niveau 2 : vocabulaire courant ---
    private fun vocabulaireCourant(): NinaBackup {
        // Les 4 colonnes du niveau 1 restent en place ; la 5e colonne ajoute des verbes
        val c5 = listOf(PERSONNE, ACTION, SOCIAL, PETIT_MOT, ACTION)
        val core = grid(
            "Moi | Vouloir | Encore | Avec | Aimer" to c5,
            "Toi | Aller | Fini | Pas | Faire" to c5,
            "Lui | Aider | Ça | Ici | Mettre" to listOf(PERSONNE, ACTION, PETIT_MOT, PETIT_MOT, ACTION),
            "Elle | Avoir | Plus | Et | Regarder" to c5
        )
        val folders = listOf(
            folder("Actions", ACTION, words(ACTION,
                "Manger", "Boire", "Jouer", "Dormir", "Écouter", "Parler", "Ouvrir", "Fermer", "Donner", "Prendre",
                "Laver", "Sortir")),
            folder("Nourriture", CHOSE, words(CHOSE,
                "Eau", "Lait", "Jus", "Pain", "Pomme", "Banane", "Fraise", "Gâteau", "Biscuit", "Yaourt",
                "Pâtes", "Riz", "Viande", "Fromage")),
            folder("Personnes", PERSONNE, words(PERSONNE,
                "Maman", "Papa", "Frère", "Sœur", "Bébé", "Mamie", "Papi", "Maîtresse", "Copain", "Docteur")),
            folder("Émotions", DESCRIPTIF, words(DESCRIPTIF,
                "Content", "Triste", "En colère", "Peur", "Fatigué", "Malade", "Calme")),
            folder("Lieux", LIEU, words(LIEU,
                "Maison", "École", "Parc", "Dehors", "Voiture", "Chambre", "Cuisine", "Salle de bain", "Magasin", "Piscine")),
            folder("Jeux", ACTION, words(CHOSE,
                "Ballon", "Poupée", "Petite voiture", "Livre", "Puzzle", "Dessin", "Musique", "Télé", "Tablette", "Vélo")),
        )
        return NinaBackup(core, folders, QUICK_PLUS)
    }

    // --- Niveau 3 : vocabulaire étendu (sous-dossiers) ---
    private fun vocabulaireEtendu(): NinaBackup {
        // Mêmes places qu'au niveau 2 ; la 6e colonne ajoute les questions, la 5e rangée de nouveaux mots
        val c6 = listOf(PERSONNE, ACTION, SOCIAL, PETIT_MOT, ACTION, QUESTION)
        val core = grid(
            "Moi | Vouloir | Encore | Avec | Aimer | Quoi" to c6,
            "Toi | Aller | Fini | Pas | Faire | Où" to c6,
            "Lui | Aider | Ça | Ici | Mettre | Qui" to listOf(PERSONNE, ACTION, PETIT_MOT, PETIT_MOT, ACTION, QUESTION),
            "Elle | Avoir | Plus | Et | Regarder | Quand" to c6,
            "Nous | Être | Grand | Pour | Prendre | Pourquoi" to listOf(PERSONNE, ACTION, DESCRIPTIF, PETIT_MOT, ACTION, QUESTION)
        )
        val folders = listOf(
            folder("Actions", ACTION, words(ACTION,
                "Manger", "Boire", "Jouer", "Dormir", "Écouter", "Parler", "Ouvrir", "Fermer", "Donner", "Laver",
                "Sortir", "Venir", "Courir", "Sauter", "Chanter", "Dessiner", "Lire", "S'habiller", "Attendre", "Chercher")),
            folder("Nourriture", CHOSE, listOf(
                folder("Repas", CHOSE, words(CHOSE, "Pain", "Pâtes", "Riz", "Viande", "Poisson", "Œuf", "Fromage", "Soupe", "Pizza", "Frites")),
                folder("Fruits et légumes", CHOSE, words(CHOSE, "Pomme", "Banane", "Fraise", "Orange", "Raisin", "Carotte", "Tomate", "Haricot")),
                folder("Goûter", CHOSE, words(CHOSE, "Gâteau", "Biscuit", "Yaourt", "Chocolat", "Bonbon", "Compote", "Crêpe")),
                folder("Boissons", CHOSE, words(CHOSE, "Eau", "Lait", "Jus", "Sirop", "Chocolat chaud")),
            )),
            folder("Personnes", PERSONNE, words(PERSONNE,
                "Maman", "Papa", "Frère", "Sœur", "Bébé", "Mamie", "Papi", "Tonton", "Tata", "Maîtresse", "Copain", "Docteur")),
            folder("Ressentis", DESCRIPTIF, listOf(
                folder("Émotions", DESCRIPTIF, words(DESCRIPTIF, "Content", "Triste", "En colère", "Peur", "Surpris", "Calme", "Fier", "Timide")),
                folder("Corps", DESCRIPTIF, words(DESCRIPTIF, "Tête", "Ventre", "Dent", "Main", "Pied", "Jambe", "Bras", "Oreille", "Œil", "Nez")),
                folder("Besoins", DESCRIPTIF, words(DESCRIPTIF, "Faim", "Soif", "Fatigué", "Froid", "Chaud", "Malade")),
            )),
            folder("Lieux", LIEU, words(LIEU,
                "Maison", "École", "Parc", "Dehors", "Voiture", "Bus", "Chambre", "Cuisine", "Salle de bain", "Magasin", "Piscine", "Plage")),
            folder("Jeux", ACTION, words(CHOSE,
                "Ballon", "Poupée", "Petite voiture", "Livre", "Puzzle", "Dessin", "Musique", "Télé", "Tablette", "Vélo", "Toboggan", "Balançoire",
                "Pâte à modeler", "Bulles")),
        )
        return NinaBackup(core, folders, QUICK_PLUS)
    }

    val all = listOf(
        VocabularyPack("premiers_mots", "Premiers mots", "Débutant",
            "12 mots essentiels et 5 petits dossiers. Pour découvrir la communication par pictogrammes.", 4, ::premiersMots),
        VocabularyPack("courant", "Vocabulaire courant", "Intermédiaire",
            "20 mots essentiels, 6 dossiers bien remplis. Pour faire des phrases de 2 à 4 mots.", 5, ::vocabulaireCourant),
        VocabularyPack("etendu", "Vocabulaire étendu", "Avancé",
            "30 mots essentiels avec les questions (quoi, où, qui…), dossiers et sous-dossiers. Pour s'exprimer sur beaucoup de sujets.", 6, ::vocabulaireEtendu),
    )

    /** Grille installée au premier lancement. */
    val default get() = all.first()

    /** Nombre total de mots (sans compter les dossiers eux-mêmes). */
    fun wordCount(layout: NinaBackup): Int {
        fun count(cells: List<AACCell>): Int = cells.sumOf { if (it.isFolder) count(it.content) else 1 }
        return count(layout.buttons) + count(layout.coreWords.orEmpty()) + count(layout.quickFires.orEmpty())
    }

    /** Tous les libellés (pour vérifier les images). */
    fun allLabels(layout: NinaBackup): List<String> {
        fun collect(cells: List<AACCell>): List<String> = cells.flatMap { listOf(it.label) + if (it.isFolder) collect(it.content) else emptyList() }
        return collect(layout.buttons) + collect(layout.coreWords.orEmpty()) + collect(layout.quickFires.orEmpty())
    }
}
